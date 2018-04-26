package com.instaclustr.backup.task;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.BackupException;
import com.instaclustr.backup.jmx.CassandraObjectNames;
import com.instaclustr.backup.uploader.FilesUploader;
import com.instaclustr.backup.util.Directories;
import com.instaclustr.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmx.org.apache.cassandra.two.zero.service.StorageServiceMBean;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

public class BackupTask implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(BackupTask.class);

    // Ver. 2.0 = instaclustr-recovery_codes-jb-1-Data.db
    // Ver. 2.1 = lb-1-big-Data.db
    // Ver. 2.2 = lb-1-big-Data.db
    // Ver. 3.0 = mc-1-big-Data.db
    private static final Pattern SSTABLE_RE = Pattern.compile("((?:[a-zA-Z0-9][a-zA-Z0-9_-]+[a-zA-Z0-9][a-zA-Z0-9_-]+-)?[a-z]{2}-(\\d+)(?:-big)?)-.*");
    private static final int SSTABLE_PREFIX_IDX = 1;
    private static final int SSTABLE_GENERATION_IDX = 2;
    private static final ImmutableList<String> DIGESTS = ImmutableList.of("crc32", "adler32", "sha1");
    // ver 2.0 sha1 file includes checksum and space separated partial filepath
    private static final Pattern CHECKSUM_RE = Pattern.compile("^([a-zA-Z0-9]+).*");

    private final JMXServiceURL cassandraJMXServiceURL;
    private final FilesUploader filesUploader;
    private final Path snapshotManifestDirectory;
    private final Path snapshotTokensDirectory;
    private final GlobalLock globalLock;

    private final Path cassandraDataDirectory;

    private final Path backupDataRootKey;
    private final Path backupManifestsRootKey;
    private final Path backupTokensRootKey;

    private final BackupArguments arguments;

    static class KeyspaceColumnFamilySnapshot {
        final String keyspace, columnFamily;
        final Path snapshotDirectory;

        public KeyspaceColumnFamilySnapshot(final Path snapshotDirectory) {
            // /data /<keyspace> /<column family> /snapshots /<snapshot>

            final Path columnFamilyDirectory = snapshotDirectory.getParent().getParent();

            this.columnFamily = columnFamilyDirectory.getFileName().toString();
            this.keyspace = columnFamilyDirectory.getParent().getFileName().toString();
            this.snapshotDirectory = snapshotDirectory;
        }
    }

    public BackupTask(final FilesUploader filesUploader,
                      final BackupArguments arguments,
                      final GlobalLock globalLock) throws IOException {
        this.cassandraJMXServiceURL = arguments.jmxServiceURL;
        this.snapshotManifestDirectory = arguments.sharedContainerPath.resolve(Paths.get("var/lib/node-agent/manifests"));
        this.snapshotTokensDirectory = arguments.sharedContainerPath.resolve(Paths.get("var/lib/node-agent/tokens"));
        this.globalLock = globalLock;

        Files.createDirectories(snapshotManifestDirectory);
        Files.createDirectories(snapshotTokensDirectory);

        this.cassandraDataDirectory = arguments.cassandraDirectory.resolve(Directories.CASSANDRA_DATA);

        this.backupDataRootKey = Paths.get("data");
        this.backupManifestsRootKey = Paths.get("manifests");
        this.backupTokensRootKey = Paths.get("tokens");

        this.filesUploader = filesUploader;
        this.arguments = arguments;
    }

    private static Runnable runOnceRunnable(final Runnable r) {
        return new Runnable() {
            public boolean hasRun = false;

            @Override
            public void run() {
                if (hasRun)
                    return;

                hasRun = true;
                r.run();
            }
        };
    }

    @Override
    public Void call() throws Exception {
        if (globalLock.getLock(arguments.waitForLock))
            call0();

        return null;
    }

    protected void call0() throws Exception {
        try (final JMXConnector jmxConnector = JMXConnectorFactory.connect(cassandraJMXServiceURL)) {

            final MBeanServerConnection cassandraMBeanServerConnection = jmxConnector.getMBeanServerConnection();
            final StorageServiceMBean storageServiceMBean = JMX.newMBeanProxy(cassandraMBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE, StorageServiceMBean.class);

            final Runnable clearSnapshotRunnable = runOnceRunnable(() -> {
                try {
                    storageServiceMBean.clearSnapshot(arguments.snapshotTag);
                    logger.info("Cleared snapshot \"{}\".", arguments.snapshotTag);

                } catch (final IOException e) {
                    logger.error("Failed to cleanup snapshot {}.", arguments.snapshotTag, e);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(clearSnapshotRunnable));

            try {
                // take snapshot
                if (arguments.columnFamily != null) {
                    final String keyspace = Iterables.getOnlyElement(arguments.keyspaces);

                    logger.info("Taking snapshot \"{}\" on {}.{}.", arguments.snapshotTag, keyspace, arguments.columnFamily);
                    storageServiceMBean.takeColumnFamilySnapshot(keyspace, arguments.columnFamily, arguments.snapshotTag);

                } else {
                    logger.info("Taking snapshot \"{}\" on {}.", arguments.snapshotTag, (arguments.keyspaces.isEmpty() ? "\"all\"" : arguments.keyspaces));
                    storageServiceMBean.takeSnapshot(arguments.snapshotTag, arguments.keyspaces.toArray(new String[arguments.keyspaces.size()]));
                }

                // Optionally drain immediately following snapshot (e.g. pre-restore)
                if (arguments.drain) {
                    storageServiceMBean.drain();
                }

                // find files belonging to snapshot
                final Map<String, ? extends Iterable<KeyspaceColumnFamilySnapshot>> snapshots = findKeyspaceColumnFamilySnapshots();
                final Iterable<KeyspaceColumnFamilySnapshot> keyspaceColumnFamilySnapshots = snapshots.get(arguments.snapshotTag);

                if (keyspaceColumnFamilySnapshots == null) {
                    if (!arguments.keyspaces.isEmpty()) {
                        logger.warn("No keyspace column family snapshot directories were found for snapshot \"{}\" of {}", arguments.snapshotTag, Joiner.on(",").join(arguments.keyspaces));
                        return;
                    }

                    // There should at least be system keyspace tables
                    throw new BackupException(String.format("No keyspace column family snapshot directories were found for snapshot \"%s\" of all data.", arguments.snapshotTag));
                }

                // generate manifest (set of object keys and source files defining the snapshot)
                final Collection<ManifestEntry> manifest = new LinkedList<>(); // linked list to maintain order

                Iterables.addAll(manifest, saveTokenList(storageServiceMBean));

                // add snapshot files to the manifest
                for (final KeyspaceColumnFamilySnapshot keyspaceColumnFamilySnapshot : keyspaceColumnFamilySnapshots) {
                    final Path bucketKey = backupDataRootKey.resolve(Paths.get(keyspaceColumnFamilySnapshot.keyspace, keyspaceColumnFamilySnapshot.columnFamily));
                    Iterables.addAll(manifest, ssTableManifest(keyspaceColumnFamilySnapshot.snapshotDirectory, bucketKey));
                }

                logger.debug("{} files in manifest for snapshot \"{}\".", manifest.size(), arguments.snapshotTag);

                if (manifest.stream().noneMatch((Predicate<ManifestEntry>) input -> input != null && input.localFile.toString().contains("-Data.db"))) {
                    throw new BackupException("No Data.db SSTables found in manifest. Aborting com.instaclustr.backup.");
                }

                Iterables.addAll(manifest, saveManifest(manifest));

                filesUploader.uploadOrFreshenFiles(manifest);

            } finally {
                clearSnapshotRunnable.run();
            }
        }
    }

    private static Map<String, List<Path>> listSSTables(Path table) throws IOException {
        return Files.list(table)
                .filter(path -> SSTABLE_RE.matcher(path.getFileName().toString()).matches())
                .collect(Collectors.groupingBy(path -> {
                    Matcher matcher = SSTABLE_RE.matcher(path.getFileName().toString());
                    //noinspection ResultOfMethodCallIgnored
                    matcher.matches();
                    return matcher.group(SSTABLE_GENERATION_IDX);
                }));
    }

    public static String calculateChecksum(final Path filePath) throws IOException {
        try(final FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {

            final FileChannel fileChannel = fileInputStream.getChannel();

            long bytesStart;
            int bytesPerChecksum = 10 * 1024 * 1024;

            // Get last 10 megabytes of file to use for checksum
            if (fileChannel.size() >= bytesPerChecksum) {
                bytesStart = fileChannel.size() - bytesPerChecksum;
            } else {
                bytesStart = 0;
                bytesPerChecksum = (int) fileChannel.size();
            }

            fileChannel.position(bytesStart);
            final byte[] bytesToChecksum = new byte[bytesPerChecksum];
            fileInputStream.read(bytesToChecksum);

            // Adler32 because it's faster than SHA / MD5 and Cassandra uses it - https://issues.apache.org/jira/browse/CASSANDRA-5862
            final Adler32 adler32 = new Adler32();
            adler32.update(bytesToChecksum);
            final long checksum = adler32.getValue();

            return String.valueOf(checksum);
        }
    }

    public static String sstableHash(Path path) throws IOException {
        final Matcher matcher = SSTABLE_RE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new BackupException("Can't compute SSTable hash for " + path + ": doesn't taste like sstable");
        }

        for (String digest : DIGESTS) {
            final Path digestPath = path.resolveSibling(matcher.group(SSTABLE_PREFIX_IDX) + "-Digest." + digest);
            if (!Files.exists(digestPath)) {
                continue;
            }

            final Matcher matcherChecksum = CHECKSUM_RE.matcher(new String(Files.readAllBytes(digestPath), StandardCharsets.UTF_8));
            if (matcherChecksum.matches()) {
                return matcher.group(SSTABLE_GENERATION_IDX) + "-" + matcherChecksum.group(1);
            }
        }

        // Ver. 2.0 doesn't create hash file, so do it ourselves
        try {
            final Path dataFilePath = path.resolveSibling(matcher.group(SSTABLE_PREFIX_IDX) + "-Data.db");
            logger.warn("No digest file found, generating checksum based on {}.", dataFilePath);
            return matcher.group(SSTABLE_GENERATION_IDX) + "-" + calculateChecksum(dataFilePath);
        } catch (IOException e) {
            throw new BackupException("Couldn't generate checksum for " + path.toString());
        }
    }

    public static Collection<ManifestEntry> ssTableManifest(Path tablePath, Path tableBackupPath) throws IOException {
        final Map<String, List<Path>> sstables = listSSTables(tablePath);

        final LinkedList<ManifestEntry> manifest = new LinkedList<>();
        for (Map.Entry<String, List<Path>> entry: sstables.entrySet()) {
            final String hash = sstableHash(entry.getValue().get(0));

            for (Path path: entry.getValue()) {
                final Path tableRelative = tablePath.relativize(path);
                final Path backupPath = tableBackupPath.resolve(hash).resolve(tableRelative);
                manifest.add(new ManifestEntry(backupPath, path, ManifestEntry.Type.FILE));
            }
        }
        return manifest;
    }

    private Iterable<ManifestEntry> saveManifest(final Iterable<ManifestEntry> manifest) throws IOException {
        final Path manifestFilePath = Files.createFile(snapshotManifestDirectory.resolve(arguments.snapshotTag));

        try (final Writer writer = Files.newBufferedWriter(manifestFilePath)) {
            for (final ManifestEntry manifestEntry : manifest) {
                writer.write(Joiner.on(' ').join(manifestEntry.size, manifestEntry.objectKey));
                writer.write('\n');
            }
        }

        manifestFilePath.toFile().deleteOnExit();

        return ImmutableList.of(new ManifestEntry(backupManifestsRootKey.resolve(manifestFilePath.getFileName()), manifestFilePath, ManifestEntry.Type.MANIFEST_FILE));
    }

    private Iterable<ManifestEntry> saveTokenList(final StorageServiceMBean storageServiceMBean) throws IOException {
        final Path tokensFilePath = snapshotTokensDirectory.resolve(String.format("%s-tokens.yaml", arguments.snapshotTag));

        try (final OutputStream stream = Files.newOutputStream(tokensFilePath); final PrintStream writer = new PrintStream(stream)) {
            writer.println("# automatically generated by cassandra-com.instaclustr.backup.");
            writer.println("# add the following to cassandra.yaml when restoring to a new cluster.");
            writer.printf("initial_token: %s%n", Joiner.on(',').join(storageServiceMBean.getTokens()));
        }

        tokensFilePath.toFile().deleteOnExit();

        return ImmutableList.of(new ManifestEntry(backupTokensRootKey.resolve(tokensFilePath.getFileName()), tokensFilePath, ManifestEntry.Type.FILE));
    }

    private Map<String, ? extends Iterable<KeyspaceColumnFamilySnapshot>> findKeyspaceColumnFamilySnapshots() throws IOException {
        // /var/lib/cassandra /data /<keyspace> /<column family> /snapshots /<snapshot>
        return Files.find(cassandraDataDirectory.resolve(Directories.CASSANDRA_DATA), 4, (path, basicFileAttributes) -> path.getParent().endsWith("snapshots"))
                .map((KeyspaceColumnFamilySnapshot::new))
                .collect(Collectors.groupingBy(k -> k.snapshotDirectory.getFileName().toString()));
    }
}
