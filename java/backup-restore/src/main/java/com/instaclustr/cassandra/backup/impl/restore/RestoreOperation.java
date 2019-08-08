package com.instaclustr.cassandra.backup.impl.restore;

import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForFullExistingRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForFullNewRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForSubsetExistingRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForSubsetNewRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.isSubsetTable;
import static com.instaclustr.io.FileUtils.cleanDirectory;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.SSTableUtils;
import com.instaclustr.io.GlobalLock;
import com.instaclustr.sidecar.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreOperation extends Operation<RestoreOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RestoreOperation.class);

    private final static String CASSANDRA_DATA = "data";
    private final static String CASSANDRA_COMMIT_LOGS = "commitlog";
    private final static String CASSANDRA_COMMIT_RESTORE = "commitlog_restore";

    private final Map<String, RestorerFactory> restorerFactoryMap;

    // maybe not necessary?
    private final Path commitLogRestoreDirectory;
    private final Path fullCommitLogRestoreDirectory;

    @Inject
    public RestoreOperation(final Map<String, RestorerFactory> restorerFactoryMap,
                            @Assisted final RestoreOperationRequest request) {
        super(request);
        this.restorerFactoryMap = restorerFactoryMap;

        this.commitLogRestoreDirectory = request.cassandraDirectory.resolve(CASSANDRA_COMMIT_RESTORE); //TODO: hardcoded path, make this an argument when we get to supporting CL
        this.fullCommitLogRestoreDirectory = request.sharedContainerPath.resolve(this.commitLogRestoreDirectory.subpath(0, this.commitLogRestoreDirectory.getNameCount()));
    }

    @Override
    protected void run0() throws Exception {
        try (final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createRestorer(request)) {
            restore(restorer);
            setTokens(restorer);
            disableAutoBootstrap();
        }
    }

    private void restore(final Restorer restorer) throws Exception {
        // 1. TODO: Check cassandra still running. Halt if running? Make this restore task a pre-start container for the pod to avoid this check

        new GlobalLock(request.sharedContainerPath).waitForLock(request.waitForLock);

        // 2. Determine if just restoring a subset of tables
        final boolean isTableSubsetOnly = request.keyspaceTables.size() > 0;

        // 3. Download the manifest
        logger.info("Retrieving manifest for snapshot: {}", request.snapshotTag);
        final Path sourceManifest = Paths.get("manifests/" + request.snapshotTag);
        final Path localManifest = request.sharedContainerPath.resolve(sourceManifest);

        final RemoteObjectReference manifestRemoteObjectReference = restorer.objectKeyToRemoteReference(sourceManifest);

        try {
            restorer.downloadFile(localManifest, manifestRemoteObjectReference);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                logger.error("Remote object reference {} does not exist.", manifestRemoteObjectReference);

                throw e;

            }
        }

        // 4. Clean out old data
        cleanDirectory(fullCommitLogRestoreDirectory.toFile());
        cleanDirectory(request.cassandraDirectory.resolve("hints").toFile());
        cleanDirectory(request.cassandraDirectory.resolve("saved_caches").toFile());

        // 5. Build a list of all SSTables currently present, that are candidates for deleting
        final Set<Path> existingSstableList = new HashSet<>();
        final int skipBackupsAndSnapshotsFolders = 4;

        final Path cassandraSstablesDirectory = request.cassandraDirectory.resolve(CASSANDRA_DATA);

        if (cassandraSstablesDirectory.toFile().exists()) {
            try (Stream<Path> paths = Files.walk(cassandraSstablesDirectory, skipBackupsAndSnapshotsFolders)) {
                if (isTableSubsetOnly) {
                    paths.filter(Files::isRegularFile)
                            .filter(isSubsetTable(request.keyspaceTables))
                            .forEach(existingSstableList::add);
                } else {
                    paths.filter(Files::isRegularFile).forEach(existingSstableList::add);
                }
            }
        }

        final boolean isRestoringToExistingCluster = existingSstableList.size() > 0;
        logger.info("Restoring to existing cluster? {}", isRestoringToExistingCluster);

        // 5. Parse the manifest
        final LinkedList<ManifestEntry> downloadManifest = new LinkedList<>();

        try (final BufferedReader manifestStream = Files.newBufferedReader(localManifest)) {
            final List<String> filteredManifest;

            if (isRestoringToExistingCluster) {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(getManifestFilesForSubsetExistingRestore(request.keyspaceTables, request.restoreSystemKeyspace))
                            .collect(toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(getManifestFilesForFullExistingRestore(request.restoreSystemKeyspace))
                            .collect(toList());
                }
            } else {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(getManifestFilesForSubsetNewRestore(request.keyspaceTables, request.restoreSystemKeyspace))
                            .collect(toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(getManifestFilesForFullNewRestore(request.restoreSystemKeyspace))
                            .collect(toList());
                }
            }

            for (final String m : filteredManifest) {
                final String[] lineArray = m.trim().split(" ");

                final Path manifestPath = Paths.get(lineArray[1]);
                final int hashPathPart = isSecondaryIndexManifest(manifestPath) ? 4 : 3;

                //strip check hash from path
                final Path localPath = request.cassandraDirectory.resolve(manifestPath.subpath(0, hashPathPart).resolve(manifestPath.getFileName()));

                if (isAnExistingSstable(localPath, manifestPath.getName(hashPathPart).toString())) {
                    logger.info("Keeping existing sstable " + localPath);
                    existingSstableList.remove(localPath);
                    continue; // file already present, and the hash matches so don't add to manifest to download and don't delete
                }

                logger.info("Not keeping existing sstable {}", localPath);
                downloadManifest.add(new ManifestEntry(manifestPath, localPath, ManifestEntry.Type.FILE, 0));
            }
        }

        // 6. Delete any entries left in existingSstableList
        existingSstableList.forEach(sstablePath -> {
            logger.info("Deleting existing sstable {}", sstablePath);
            if (!sstablePath.toFile().delete()) {
                logger.warn("Failed to delete {}", sstablePath);
            }
        });

        // 7. Download files in the manifest
        restorer.downloadFiles(downloadManifest);

        // 8. download tokens
    }

    /**
     * Decides whether or not the manifest path includes secondary index files
     *
     * @param manifestPath path to manifest
     * @return true if manifest path includes secondary index files, false otherwise
     */
    private boolean isSecondaryIndexManifest(final Path manifestPath) {
        // When there's a secondary index, manifest path contains 6 elements (including '.indexName' and 'hashcode')
        // '.indexName' is filtered by subpath(3,4), to avoid the other parts of the manifest path getting misidentified with the '.'
        return manifestPath.getNameCount() == 6 && manifestPath.subpath(3, 4).toString().startsWith(".");
    }

    private boolean isAnExistingSstable(final Path localPath, final String sstable) {
        try {
            if (localPath.toFile().exists() && SSTableUtils.sstableHash(localPath).equals(sstable)) {
                return true;
            }
        } catch (IOException e) {
            // SSTableUtils.sstableHash may throw exception if SSTable has not been probably downloaded
            logger.error(e.getMessage());
        }
        return false;
    }

    private void setTokens(final Restorer restorer) throws Exception {
        final RemoteObjectReference tokens = restorer.objectKeyToRemoteReference(Paths.get("tokens/" + request.snapshotTag + "-tokens.yaml"));
        final Path tokensPath = request.cassandraDirectory.resolve("tokens.yaml");
        // TODO this should be added to one of directories for configuration as it is a config fragment to yaml
        restorer.downloadFile(tokensPath, tokens);
    }

    private void disableAutoBootstrap() {
        // TODO prepare fragment and place it to dir cassandra picks up to configure itself
//        final StringBuilder stringBuilder = new StringBuilder();
//        final String contents = new String(Files.readAllBytes(cassandraConfigDirectory.resolve("cassandra.yaml")));
//        // Just replace. In case nodepoint later doesn't write auto_bootstrap, just delete here and append later to guarantee we're setting it
//        stringBuilder.append(contents.replace("auto_bootstrap: true", ""));
//
//        stringBuilder.append(System.lineSeparator());
//        stringBuilder.append(new String(Files.readAllBytes(tokensPath)));
//        // Don't stream on Cassandra startup, as tokens and SSTables are already present on node
//        stringBuilder.append("auto_bootstrap: false");
//        Files.write(cassandraConfigDirectory.resolve("cassandra.yaml"), ImmutableList.of(stringBuilder.toString()), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }
}
