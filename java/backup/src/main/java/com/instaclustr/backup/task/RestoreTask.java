package com.instaclustr.backup.task;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.common.RemoteObjectReference;
import com.instaclustr.backup.downloader.Downloader;
import com.instaclustr.backup.common.CloudDownloadUploadFactory;
import com.instaclustr.backup.util.Directories;
import com.instaclustr.backup.util.FileUtils;
import com.instaclustr.backup.util.GlobalLock;
import com.microsoft.azure.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestoreTask implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(RestoreTask.class);

    private final Downloader downloaderProvider;
    private final GlobalLock globalLock;
    private final RestoreArguments arguments;
    private final Path cassandraDataDirectory;
    private final Path cassandraConfigDirectory;
    private final Path sharedContainerRoot;
    private final Path commitLogRestoreDirectory;
    private final Path fullCommitLogRestoreDirectory;
    private final Multimap<String, String> keyspaceTableSubset;
    private boolean enableCommitLogRestore = false;

    public RestoreTask(final GlobalLock globalLock,
                       final RestoreArguments arguments
    ) throws StorageException, ConfigurationException, URISyntaxException, InvalidKeyException {





        this.downloaderProvider = CloudDownloadUploadFactory.getDownloader(arguments);
        this.globalLock = globalLock;
        this.arguments = arguments;
        this.cassandraDataDirectory = arguments.cassandraDirectory; //TODO change to cassandra root directory
        this.cassandraConfigDirectory = arguments.cassandraConfigDirectory;
        this.sharedContainerRoot = arguments.sharedContainerPath;
        this.commitLogRestoreDirectory = arguments.cassandraDirectory.resolve("commitlog_restore"); //TODO: hardcoded path, make this an argument when we get to supporting CL
        this.fullCommitLogRestoreDirectory = this.sharedContainerRoot.resolve(this.commitLogRestoreDirectory.subpath(0, this.commitLogRestoreDirectory.getNameCount()));
        this.keyspaceTableSubset = arguments.keyspaceTables;
    }

    private Map<String, String> restoreParameters(final RestoreArguments restoreArguments) {
        return new HashMap<String, String>() {{
            put("snapshot-tag", restoreArguments.snapshotTag);
            put("timestamp-start", String.valueOf(restoreArguments.timestampStart));
            put("timestamp-end", String.valueOf(restoreArguments.timestampEnd));
            put("keyspace-tables", restoreArguments.keyspaceTables.entries()
                    .stream()
                    .map(x -> x.getKey() + "." + x.getValue())
                    .reduce((x,y) -> x + "," + y ).orElse(""));
            put("sourceNodeID", restoreArguments.clusterId);
            put("cluster", restoreArguments.clusterId);
            put("com.instaclustr.backup-bucket", restoreArguments.backupBucket);
            put("restore-system-keyspace", String.valueOf(restoreArguments.restoreSystemKeyspace));
        }};
    }

    @Override
    public Void call() throws Exception {
        if (globalLock.getLock(arguments.waitForLock)) {
           logger.info("Restoring backup {}", restoreParameters(arguments));
            try {
                call0();
                logger.info("Completed restoring backup {}", restoreParameters(arguments));

            } catch (Exception e) {
                logger.info("Failed restoring backup {} with {}", restoreParameters(arguments), e);
                throw e;
            }
        }
        return null;
    }

    private void call0() throws Exception {
        // 1. TODO: Check cassandra still running. Halt if running? Make this restore task a pre-start container for the pod to avoid this check

        // 2. Determine if just restoring a subset of tables
        final boolean isTableSubsetOnly = keyspaceTableSubset.size() > 0;

        // 3. Download the manifest
        logger.info("Retrieving manifest for snapshot: {}", arguments.snapshotTag);
        final Path sourceManifest = Paths.get("manifests/" + arguments.snapshotTag);
        final Path localManifest = sharedContainerRoot.resolve(sourceManifest);

        final Downloader downloader = downloaderProvider;
        final RemoteObjectReference manifestRemoteObjectReference = downloader.objectKeyToRemoteReference(sourceManifest);
        downloader.downloadFile(localManifest, manifestRemoteObjectReference);

        // 4. Clean out old data
        FileUtils.cleanDirectory(fullCommitLogRestoreDirectory.toFile());
        FileUtils.cleanDirectory(cassandraDataDirectory.resolve("hints").toFile());
        FileUtils.cleanDirectory(cassandraDataDirectory.resolve("saved_caches").toFile());

        // 5. Build a list of all SSTables currently present, that are candidates for deleting
        final Set<Path> existingSstableList = new HashSet<>();
        final int skipBackupsAndSnapshotsFolders = 3;

        final Path cassandraSstablesDirectory = cassandraDataDirectory.resolve(Directories.CASSANDRA_DATA);

        if (cassandraSstablesDirectory.toFile().exists()) {
            try (Stream<Path> paths = Files.walk(cassandraDataDirectory.resolve(Directories.CASSANDRA_DATA), skipBackupsAndSnapshotsFolders)) {
                if (isTableSubsetOnly) {
                    paths.filter(Files::isRegularFile)
                            .filter(RestorePredicates.isSubsetTable(keyspaceTableSubset))
                            .forEach(existingSstableList::add);
                } else {
                    paths.filter(Files::isRegularFile)
                            .forEach(existingSstableList::add);
                }
            }
        }

        final boolean isRestoringToExistingCluster = existingSstableList.size() > 0;
        logger.info("Restoring to existing cluster? {}", String.valueOf(isRestoringToExistingCluster));

        // 5. Parse the manifest
        LinkedList<ManifestEntry> downloadManifest = new LinkedList<>();

        try (BufferedReader manifestStream = Files.newBufferedReader(localManifest)) {
            List<String> filteredManifest;

            if (isRestoringToExistingCluster) {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForSubsetExistingRestore(logger, keyspaceTableSubset))
                            .collect(Collectors.toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForFullExistingRestore(logger))
                            .collect(Collectors.toList());
                }
            } else {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForSubsetNewRestore(logger, keyspaceTableSubset))
                            .collect(Collectors.toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForFullNewRestore(logger))
                            .collect(Collectors.toList());
                }
            }

            for (final String m : filteredManifest) {
                final String[] lineArray = m.trim().split(" ");

                final Path manifestPath = Paths.get(lineArray[1]);
                final Path localPath = cassandraDataDirectory.resolve(manifestPath.subpath(0, 3).resolve(manifestPath.getFileName())); //strip check hash from path

                if (localPath.toFile().exists() && BackupTask.sstableHash(localPath).equals(manifestPath.getName(3).toString())) {
                    logger.info("Keeping existing sstable " + localPath);
                    existingSstableList.remove(localPath);
                    continue; // file already present, and the hash matches so don't add to manifest to download and don't delete
                }

                downloadManifest.add(new ManifestEntry(manifestPath, localPath, ManifestEntry.Type.FILE, 0));
            };
        }

        // 6. Delete any entries left in existingSstableList
        existingSstableList.forEach(sstablePath -> {
            logger.info("Deleting existing sstable {}", sstablePath);
            if (!sstablePath.toFile().delete()) {
                logger.warn("Failed to delete {}", sstablePath);
            }
        });

        // 7. Download files in the manifest
        downloader.downloadFiles(downloadManifest, "snapshot-download", arguments.concurrentConnections);

        if (enableCommitLogRestore) {
            // 8. Download commitlogs
            downloadCommitLogs(downloader, arguments.timestampStart, arguments.timestampEnd);

            // 9. Update config to initiate a restore
            final Path commitlogArchivingPropertiesPath = cassandraConfigDirectory.resolve("commitlog_archiving.properties");
            java.util.Properties commitlogArchivingProperties = new java.util.Properties();

            if (commitlogArchivingPropertiesPath.toFile().exists())
                try (final BufferedReader reader = Files.newBufferedReader(commitlogArchivingPropertiesPath, StandardCharsets.UTF_8)) {
                    commitlogArchivingProperties.load(reader);
                } catch (final IOException e) {
                    logger.warn("Failed to load file \"{}\".", commitlogArchivingPropertiesPath, e);
                }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");

            commitlogArchivingProperties.setProperty("restore_command", "cp -f %from %to");
            commitlogArchivingProperties.setProperty("restore_directories", commitLogRestoreDirectory.toString());
            // Restore mutations created up to and including this timestamp in GMT.
            // Format: yyyy:MM:dd HH:mm:ss (2012:04:31 20:43:12)
            commitlogArchivingProperties.setProperty("restore_point_in_time", dateFormat.format(new Date(arguments.timestampEnd)));

            try (OutputStream output = new FileOutputStream(commitlogArchivingPropertiesPath.toFile())) {
                commitlogArchivingProperties.store(output, null);
            } catch (final IOException e) {
                logger.warn("Failed to write to file \"{}\".", commitlogArchivingPropertiesPath, e);
            }
        }



        writeConfigOptions(downloader, isTableSubsetOnly);
    }

    private void downloadCommitLogs(final Downloader downloader, final long timestampStart, final long timestampEnd) throws Exception {
        final Set<Path> existingCommitlogsList = new HashSet<>();
        final Path commitlogsPath = cassandraDataDirectory.resolve(Directories.CASSANDRA_COMMIT_LOGS);

        if (commitlogsPath.toFile().exists())
            try (Stream<Path> paths = Files.list(commitlogsPath)) {
                paths.filter(Files::isRegularFile)
                        .forEach(existingCommitlogsList::add);
            }

        if (existingCommitlogsList.size() > 0) {
            final Path currentCommitlogsPath = commitlogsPath.getParent().resolve("commitlogs-" + String.valueOf(System.currentTimeMillis()));

            if (!currentCommitlogsPath.toFile().exists())
                Files.createDirectory(currentCommitlogsPath);

            for (Path file : existingCommitlogsList) {
                Files.move(file, currentCommitlogsPath.resolve(file.getFileName()));
            }
        }

        final RemoteObjectReference remoteObjectReference = downloader.objectKeyToRemoteReference(Paths.get(Directories.CASSANDRA_COMMIT_LOGS));
        List<RemoteObjectReference> commitlogList = downloader.listFiles(remoteObjectReference);

        // Sorting oldest to latest should still work even if CommitLog schema version gets incremented
        commitlogList.sort(Comparator.comparing(RemoteObjectReference::getObjectKey));

        final Pattern commitlogPattern = Pattern.compile(".*(CommitLog-\\d+-\\d+\\.log)\\.(\\d+)");
        final HashSet<ManifestEntry> parsedCommitlogList = new HashSet<>();

        for (RemoteObjectReference commitlogFile : commitlogList) {
            Matcher matcherCommitlog = commitlogPattern.matcher(commitlogFile.getObjectKey().toString());

            if (!matcherCommitlog.matches()) {
                continue;
            }

            final long commitlogTimestamp = Long.parseLong(matcherCommitlog.group(2));

            if (commitlogTimestamp >= timestampStart) {
                parsedCommitlogList.add(new ManifestEntry(commitlogFile.getObjectKey(), fullCommitLogRestoreDirectory.resolve(matcherCommitlog.group(1)), ManifestEntry.Type.FILE, 0));
            }

            if (commitlogTimestamp > timestampEnd) {
                break;
            }
        }

        if (parsedCommitlogList.size() == 0) {
            return;
        }

        downloader.downloadFiles(parsedCommitlogList, "commitlogs-download", arguments.concurrentConnections);
    }

    private void writeConfigOptions(final Downloader downloader, final boolean isTableSubsetOnly) throws Exception {
        final StringBuilder cassandraEnvStringBuilder = new StringBuilder();

        if (enableCommitLogRestore) {
            if (isTableSubsetOnly) {
                cassandraEnvStringBuilder
                        .append("JVM_OPTS=\"$JVM_OPTS -Dcassandra.replayList=")
                        .append(Joiner.on(",").withKeyValueSeparator(".").join(keyspaceTableSubset.entries()))
                        .append("\"\n");
            }

            Files.write(cassandraConfigDirectory.resolve("cassandra-env.sh"), cassandraEnvStringBuilder.toString().getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        }
        // Can't write to cassandra-env.sh as nodetool could generate "File name too long" error
    
        logger.info("download tokens.yaml");
        RemoteObjectReference tokens = downloader.objectKeyToRemoteReference(Paths.get("tokens/" + arguments.snapshotTag + "-tokens.yaml"));
        final Path tokensPath = sharedContainerRoot.resolve("tokens.yaml");
        downloader.downloadFile(tokensPath, tokens);

        // add a config fragment with initial_token and auto_bootstrap disabled
        logger.info("generate cassandra fragment config");
        final Path configDir = cassandraConfigDirectory.resolve("cassandra.yaml.d");
        Files.createDirectories(configDir);
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(new String(Files.readAllBytes(tokensPath)));
        stringBuilder.append(System.lineSeparator());
        // Don't stream on Cassandra startup, as tokens and SSTables are already present on node
        stringBuilder.append("auto_bootstrap: false");
        Files.write(configDir.resolve("100-backup-restore.yaml"), ImmutableList.of(stringBuilder.toString()), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }
}
