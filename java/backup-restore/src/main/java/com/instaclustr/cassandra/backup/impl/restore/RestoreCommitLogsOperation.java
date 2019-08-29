package com.instaclustr.cassandra.backup.impl.restore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreCommitLogsOperation extends Operation<RestoreCommitLogsOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RestoreCommitLogsOperation.class);

    private final static String CASSANDRA_COMMIT_LOGS = "commitlog";

    private final Map<String, RestorerFactory> restorerFactoryMap;

    private final Path commitlogsPath;

    @Inject
    public RestoreCommitLogsOperation(final Map<String, RestorerFactory> restorerFactoryMap,
                                      @Assisted final RestoreCommitLogsOperationRequest request) {
        super(request);

        this.restorerFactoryMap = restorerFactoryMap;
        commitlogsPath = request.cassandraDirectory.resolve(CASSANDRA_COMMIT_LOGS);
    }

    @Override
    protected void run0() throws Exception {
        try (final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createCommitLogRestorer(request)) {
            backupCurrentCommitLogs();
            downloadCommitLogs(restorer);
            writeConfigOptions();
        }
    }

    private void backupCurrentCommitLogs() throws Exception {
        final Set<Path> existingCommitlogsList = new HashSet<>();

        if (commitlogsPath.toFile().exists())
            try (Stream<Path> paths = Files.list(commitlogsPath)) {
                paths.filter(Files::isRegularFile).forEach(existingCommitlogsList::add);
            }

        if (existingCommitlogsList.size() > 0) {
            final Path currentCommitlogsPath = commitlogsPath.getParent().resolve("commitlogs-" + System.currentTimeMillis());

            if (!currentCommitlogsPath.toFile().exists())
                Files.createDirectory(currentCommitlogsPath);

            for (final Path file : existingCommitlogsList) {
                Files.move(file, currentCommitlogsPath.resolve(file.getFileName()));
            }
        }
    }

    private void downloadCommitLogs(final Restorer restorer) throws Exception {
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
                                                              commitlogsPath.resolve(matcherCommitlog.group(1)),
                                                              ManifestEntry.Type.FILE,
                                                              0));
                } else if (commitlogTimestamp > request.timestampEnd && commitlogTimestamp < overhangingTimestamp.get()) {
                    // Make sure we also catch the first commitlog that goes past the end of the timestamp
                    overhangingTimestamp.set(commitlogTimestamp);
                    overhangingManifestEntry.set(new ManifestEntry(commitlogFile.getObjectKey(),
                                                                   commitlogsPath.resolve(matcherCommitlog.group(1)),
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

    /**
     * Add cassandra.replayList at the end of cassandra-env.sh. In case Cassandra container restarts,
     * this change will not be there anymore because configuration volume is ephemeral and gets
     * reconstructed every time from scratch.
     * <p>
     * However, when restoration tooling is run just against "vanilla" C* (without K8S), that added line will be there
     * "for ever".
     *
     * @throws IOException
     */
    private void writeConfigOptions() throws IOException {
        if (request.keyspaceTables.size() > 0) {

            final String cassandraEnvStringBuilder = "JVM_OPTS=\"$JVM_OPTS -Dcassandra.replayList=" +
                    Joiner.on(",").withKeyValueSeparator(".").join(request.keyspaceTables.entries()) +
                    "\"\n";

            Files.write(request.cassandraConfigDirectory.resolve("cassandra-env.sh"),
                        cassandraEnvStringBuilder.getBytes(),
                        StandardOpenOption.APPEND,
                        StandardOpenOption.CREATE);
        }
    }
}
