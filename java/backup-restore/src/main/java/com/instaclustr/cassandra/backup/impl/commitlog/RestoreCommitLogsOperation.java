package com.instaclustr.cassandra.backup.impl.commitlog;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.restore.BaseRestoreOperation;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreCommitLogsOperation extends BaseRestoreOperation<RestoreCommitLogsOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RestoreCommitLogsOperation.class);

    private final static String CASSANDRA_COMMIT_LOGS = "commitlog";
    private final static String CASSANDRA_COMMIT_RESTORE = "commitlog_restore";

    private final Map<String, RestorerFactory> restorerFactoryMap;

    private final Path fullCommitLogRestoreDirectory;
    private final Path commitLogRestoreDirectory;

    @Inject
    public RestoreCommitLogsOperation(final Map<String, RestorerFactory> restorerFactoryMap,
                                      @Assisted final RestoreCommitLogsOperationRequest request) {
        super(request);

        this.restorerFactoryMap = restorerFactoryMap;

        this.commitLogRestoreDirectory = request.cassandraDirectory.resolve(CASSANDRA_COMMIT_RESTORE); //TODO: hardcoded path, make this an argument when we get to supporting CL
        this.fullCommitLogRestoreDirectory = request.sharedContainerPath.resolve(this.commitLogRestoreDirectory.subpath(0, this.commitLogRestoreDirectory.getNameCount()));
    }

    @Override
    protected void run0() throws Exception {
        try (final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createRestorer(request)) {
            restorer.restore();

            downloadCommitLogs(restorer);

            updateConfigurationForRestoreInitiation();

            writeConfigOptions(restorer, request.keyspaceTables.size() > 0);
        }
    }

    private void updateConfigurationForRestoreInitiation() {
        final Path commitlogArchivingPropertiesPath = request.cassandraDirectory.resolve("commitlog_archiving.properties");
        final Properties commitlogArchivingProperties = new Properties();

        if (commitlogArchivingPropertiesPath.toFile().exists())
            try (final BufferedReader reader = Files.newBufferedReader(commitlogArchivingPropertiesPath, UTF_8)) {
                commitlogArchivingProperties.load(reader);
            } catch (final IOException e) {
                logger.warn("Failed to load file \"{}\".", commitlogArchivingPropertiesPath, e);
            }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");

        commitlogArchivingProperties.setProperty("restore_command", "cp -f %from %to");
        commitlogArchivingProperties.setProperty("restore_directories", commitLogRestoreDirectory.toString());
        // Restore mutations created up to and including this timestamp in GMT.
        // Format: yyyy:MM:dd HH:mm:ss (2012:04:31 20:43:12)
        commitlogArchivingProperties.setProperty("restore_point_in_time", dateFormat.format(new Date(request.timestampEnd)));

        try (OutputStream output = new FileOutputStream(commitlogArchivingPropertiesPath.toFile())) {
            commitlogArchivingProperties.store(output, null);
        } catch (final IOException e) {
            logger.warn("Failed to write to file \"{}\".", commitlogArchivingPropertiesPath, e);
        }
    }

    private void downloadCommitLogs(final Restorer restorer) throws Exception {
        final Set<Path> existingCommitlogsList = new HashSet<>();
        final Path commitlogsPath = request.cassandraDirectory.resolve(CASSANDRA_COMMIT_LOGS);

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

        final RemoteObjectReference remoteObjectReference = restorer.objectKeyToRemoteReference(Paths.get("commitlog"));
        final Pattern commitlogPattern = Pattern.compile(".*(CommitLog-\\d+-\\d+\\.log)\\.(\\d+)");
        final HashSet<ManifestEntry> parsedCommitlogList = new HashSet<>();

        logger.info("Commencing processing of commit log listing");
        final AtomicReference<ManifestEntry> overhangingManifestEntry = new AtomicReference<>();
        final AtomicLong overhangingTimestamp = new AtomicLong(Long.MAX_VALUE);

        restorer.consumeFiles(remoteObjectReference, commitlogFile -> {

            final Matcher matcherCommitlog = commitlogPattern.matcher(commitlogFile.getObjectKey().toString());

            if (matcherCommitlog.matches()) {
                final long commitlogTimestamp = Long.parseLong(matcherCommitlog.group(2));

                if (commitlogTimestamp >= request.timestampStart && commitlogTimestamp <= request.timestampEnd) {
                    parsedCommitlogList.add(new ManifestEntry(commitlogFile.getObjectKey(),
                                                              fullCommitLogRestoreDirectory.resolve(matcherCommitlog.group(1)),
                                                              ManifestEntry.Type.FILE,
                                                              0));
                } else if (commitlogTimestamp > request.timestampEnd && commitlogTimestamp < overhangingTimestamp.get()) {
                    // Make sure we also catch the first commitlog that goes past the end of the timestamp
                    overhangingTimestamp.set(commitlogTimestamp);
                    overhangingManifestEntry.set(new ManifestEntry(commitlogFile.getObjectKey(),
                                                                   fullCommitLogRestoreDirectory.resolve(matcherCommitlog.group(1)),
                                                                   ManifestEntry.Type.FILE,
                                                                   0));
                }
            }
        });
        if (overhangingManifestEntry.get() != null) {
            parsedCommitlogList.add(overhangingManifestEntry.get());
        }

        logger.info("Found {} commit logs to download", parsedCommitlogList.size());

        if (parsedCommitlogList.size() == 0) {
            return;
        }

        restorer.downloadFiles(parsedCommitlogList);
    }
}
