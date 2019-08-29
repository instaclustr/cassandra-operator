package com.instaclustr.cassandra.backup.impl.backup;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.BackuperFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.cassandra.backup.impl.SSTableUtils;
import com.instaclustr.io.GlobalLock;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationRequest;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupOperation extends Operation<BackupOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(BackupOperation.class);

    private final Provider<StorageServiceMBean> storageServiceMBeanProvider;
    private final Map<String, BackuperFactory> backuperFactoryMap;

    @Inject
    public BackupOperation(
            final Provider<StorageServiceMBean> storageServiceMBeanProvider,
            final Map<String, BackuperFactory> backuperFactoryMap,
            @Assisted final BackupOperationRequest request) {
        super(request);
        this.storageServiceMBeanProvider = storageServiceMBeanProvider;
        this.backuperFactoryMap = backuperFactoryMap;
    }

    @Override
    protected void run0() throws Exception {
        logger.info(request.toString());

        new GlobalLock(request.sharedContainerPath).waitForLock(request.waitForLock);

        if (request.offlineSnapshot) {
            executeUpload(ImmutableList.of());

            return;
        }

        final StorageServiceMBean storageServiceMBean = this.storageServiceMBeanProvider.get();

        try {
            new TakeSnapshotOperation(storageServiceMBean,
                                      new TakeSnapshotOperation.TakeSnapshotOperationRequest(request.keyspaces,
                                                                                             request.snapshotTag,
                                                                                             request.table)).run0();
            executeUpload(storageServiceMBean.getTokens());
        } finally {
            new ClearSnapshotOperation(storageServiceMBean,
                                       new ClearSnapshotOperation.ClearSnapshotOperationRequest(request.snapshotTag)).run0();
        }
    }

    private void executeUpload(List<String> tokens) throws Exception {
        final Collection<ManifestEntry> manifest = generateManifest(request.keyspaces,
                                                                    request.snapshotTag,
                                                                    request.cassandraDirectory.resolve("data"));

        Iterables.addAll(manifest, saveTokenList(tokens));
        Iterables.addAll(manifest, saveManifest(manifest, request.snapshotTag));

        try (final Backuper backuper = backuperFactoryMap.get(request.storageLocation.storageProvider).createBackuper(request)) {
            backuper.uploadOrFreshenFiles(manifest, new OperationProgressTracker(this, manifest.size()));
        }
    }

    private Collection<ManifestEntry> generateManifest(
            final List<String> keyspaces,
            final String snapshotTag,
            final Path cassandraDataDirectory) throws IOException {
        // find files belonging to snapshot
        final Map<String, ? extends Iterable<KeyspaceColumnFamilySnapshot>> snapshots = findKeyspaceColumnFamilySnapshots(cassandraDataDirectory);

        final Iterable<KeyspaceColumnFamilySnapshot> keyspaceColumnFamilySnapshots = snapshots.get(snapshotTag);

        if (keyspaceColumnFamilySnapshots == null) {
            if (keyspaces != null && !keyspaces.isEmpty()) {
                logger.warn("No keyspace column family snapshot directories were found for snapshot \"{}\" of {}", snapshotTag, Joiner.on(",").join(keyspaces));
                return new LinkedList<>();
            }

            // There should at least be system keyspace tables
            throw new IllegalStateException(format("No keyspace column family snapshot directories were found for snapshot \"%s\" of all data.", snapshotTag));
        }

        // generate manifest (set of object keys and source files defining the snapshot)
        final Collection<ManifestEntry> manifest = new LinkedList<>(); // linked list to maintain order

        // add snapshot files to the manifest
        for (final KeyspaceColumnFamilySnapshot keyspaceColumnFamilySnapshot : keyspaceColumnFamilySnapshots) {
            final Path bucketKey = Paths.get("data").resolve(Paths.get(keyspaceColumnFamilySnapshot.keyspace, keyspaceColumnFamilySnapshot.table));
            Iterables.addAll(manifest, SSTableUtils.ssTableManifest(keyspaceColumnFamilySnapshot.snapshotDirectory, bucketKey).collect(toList()));
        }

        logger.debug("{} files in manifest for snapshot \"{}\".", manifest.size(), snapshotTag);

        if (manifest.stream().noneMatch((Predicate<ManifestEntry>) input -> input != null && input.localFile.toString().contains("-Data.db"))) {
            throw new IllegalStateException("No Data.db SSTables found in manifest. Aborting backup.");
        }

        return manifest;
    }

    private Iterable<ManifestEntry> saveManifest(final Iterable<ManifestEntry> manifest, String tag) throws IOException {
        final Path snapshotManifestDirectory = Files.createDirectories(request.sharedContainerPath.resolve(Paths.get("tmp/cassandra-operator/manifests")));
        final Path manifestFilePath = snapshotManifestDirectory.resolve(tag);

        Files.deleteIfExists(manifestFilePath);
        Files.createFile(manifestFilePath);

        try (final OutputStream stream = Files.newOutputStream(manifestFilePath);
             final PrintStream writer = new PrintStream(stream)) {
            for (final ManifestEntry manifestEntry : manifest) {
                writer.println(Joiner.on(' ').join(manifestEntry.size, manifestEntry.objectKey));
            }
        }

        // TODO - clean this up! dont wait until jvm is shut down, what if this runs in sidecar?
        manifestFilePath.toFile().deleteOnExit();

        return ImmutableList.of(new ManifestEntry(Paths.get("manifests").resolve(manifestFilePath.getFileName()),
                                                  manifestFilePath,
                                                  ManifestEntry.Type.MANIFEST_FILE));
    }

    private Iterable<ManifestEntry> saveTokenList(List<String> tokens) throws IOException {
        final Path tokensDirectory = Files.createDirectories(request.sharedContainerPath.resolve(Paths.get("tmp/cassandra-operator/tokens")));
        final Path tokensFilePath = tokensDirectory.resolve(format("%s-tokens.yaml", request.snapshotTag));

        Files.deleteIfExists(tokensFilePath);
        Files.createFile(tokensFilePath);

        try (final OutputStream stream = Files.newOutputStream(tokensFilePath);
             final PrintStream writer = new PrintStream(stream)) {
            writer.println("# automatically generated by cassandra-backup");
            writer.println("# add the following to cassandra.yaml when restoring to a new cluster.");
            writer.printf("initial_token: %s%n", Joiner.on(',').join(tokens));
        }

        return ImmutableList.of(new ManifestEntry(Paths.get("tokens").resolve(tokensFilePath.getFileName()),
                                                  tokensFilePath,
                                                  ManifestEntry.Type.FILE));
    }

    private static Map<String, ? extends Iterable<KeyspaceColumnFamilySnapshot>> findKeyspaceColumnFamilySnapshots(final Path cassandraDataDirectory) throws IOException {
        // /var/lib/cassandra /data /<keyspace> /<column family> /snapshots /<snapshot>
        return Files.find(cassandraDataDirectory,
                          4,
                          (path, basicFileAttributes) -> path.getParent().endsWith("snapshots"))
                    .map((KeyspaceColumnFamilySnapshot::new))
                    .collect(groupingBy(k -> k.snapshotDirectory.getFileName().toString()));
    }

    static class KeyspaceColumnFamilySnapshot {
        final String keyspace, table;
        final Path snapshotDirectory;

        KeyspaceColumnFamilySnapshot(final Path snapshotDirectory) {
            // /data /<keyspace> /<column family> /snapshots /<snapshot>

            final Path columnFamilyDirectory = snapshotDirectory.getParent().getParent();

            this.table = columnFamilyDirectory.getFileName().toString();
            this.keyspace = columnFamilyDirectory.getParent().getFileName().toString();
            this.snapshotDirectory = snapshotDirectory;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                              .add("keyspace", keyspace)
                              .add("table", table)
                              .add("snapshotDirectory", snapshotDirectory)
                              .toString();
        }
    }

    public static class ClearSnapshotOperation extends Operation<ClearSnapshotOperation.ClearSnapshotOperationRequest> {
        private static final Logger logger = LoggerFactory.getLogger(ClearSnapshotOperation.class);

        private final StorageServiceMBean storageServiceMBean;
        private boolean hasRun = false;

        ClearSnapshotOperation(
                final StorageServiceMBean storageServiceMBean,
                final ClearSnapshotOperationRequest request) {
            super(request);
            this.storageServiceMBean = storageServiceMBean;
        }

        @Override
        protected void run0() {
            if (hasRun) {
                return;
            }

            hasRun = true;

            try {
                storageServiceMBean.clearSnapshot(request.snapshotTag);
                logger.info("Cleared snapshot {}.", request.snapshotTag);

            } catch (final IOException ex) {
                logger.error("Failed to cleanup snapshot {}.", request.snapshotTag, ex);
            }
        }

        static class ClearSnapshotOperationRequest extends OperationRequest {
            final String snapshotTag;

            ClearSnapshotOperationRequest(final String snapshotTag) {
                this.snapshotTag = snapshotTag;
            }
        }

    }

    public static class TakeSnapshotOperation extends Operation<TakeSnapshotOperation.TakeSnapshotOperationRequest> {
        private static final Logger logger = LoggerFactory.getLogger(TakeSnapshotOperation.class);

        private final TakeSnapshotOperationRequest request;
        private final StorageServiceMBean storageServiceMBean;

        TakeSnapshotOperation(
                final StorageServiceMBean storageServiceMBean,
                final TakeSnapshotOperationRequest request) {
            super(request);
            this.request = request;
            this.storageServiceMBean = storageServiceMBean;
        }

        @Override
        protected void run0() throws Exception {
            if (request.table != null) {
                final String keyspace = Iterables.getOnlyElement(request.keyspaces);

                logger.info("Taking snapshot {} on {}.{}.", request.tag, keyspace, request.table);
                // Currently only supported option by Cassandra during snapshot is to skipFlush
                // An empty map is used as skipping flush is currently not implemented.
                storageServiceMBean.takeTableSnapshot(keyspace, request.table, request.tag);

            } else {
                logger.info("Taking snapshot \"{}\" on {}.", request.tag, (request.keyspaces.isEmpty() ? "\"all\"" : request.keyspaces));
                storageServiceMBean.takeSnapshot(request.tag, request.keyspaces.toArray(new String[0]));
            }
        }

        static class TakeSnapshotOperationRequest extends OperationRequest {
            final List<String> keyspaces;
            final String tag;
            final String table;

            TakeSnapshotOperationRequest(
                    final List<String> keyspaces,
                    final String tag,
                    final String table) {
                this.keyspaces = keyspaces == null ? ImmutableList.of() : keyspaces;
                this.tag = tag;
                this.table = table;
            }
        }
    }
}
