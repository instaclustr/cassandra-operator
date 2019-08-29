package com.instaclustr.cassandra.backup.impl.backup;

import javax.inject.Inject;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.BackuperFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.io.GlobalLock;
import com.instaclustr.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupCommitLogsOperation extends Operation<BackupCommitLogsOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(BackupCommitLogsOperation.class);
    private static final String CASSANDRA_COMMIT_LOGS = "commitlog";

    private final Map<String, BackuperFactory> backuperFactoryMap;

    @Inject
    public BackupCommitLogsOperation(final Map<String, BackuperFactory> backuperFactoryMap,
                                     @Assisted final BackupCommitLogsOperationRequest request) {
        super(request);
        this.backuperFactoryMap = backuperFactoryMap;
    }

    @Override
    protected void run0() throws Exception {
        logger.info(request.toString());

        new GlobalLock(request.sharedContainerPath).waitForLock(request.waitForLock);

        // generate manifest (set of object keys and source files defining the upload)
        final Collection<ManifestEntry> manifest = new LinkedList<>(); // linked list to maintain order
        final Pattern pattern = Pattern.compile("CommitLog-\\d+-\\d+\\.log");
        final DirectoryStream.Filter<Path> filter = entry -> Files.isRegularFile(entry) && pattern.matcher(entry.getFileName().toString()).matches();

        final Path commitLogArchiveDirectory = resolveCommitLogsPath(request);
        final Path backupCommitLogRootKey = Paths.get(CASSANDRA_COMMIT_LOGS);

        try (final DirectoryStream<Path> commitLogs = Files.newDirectoryStream(commitLogArchiveDirectory, filter);
             final Backuper backuper = backuperFactoryMap.get(request.storageLocation.storageProvider).createCommitLogBackuper(request)) {

            for (final Path commitLog : commitLogs) {
                // Append file modified date so we have some idea of the time range this commitlog covers
                final Path bucketKey = backupCommitLogRootKey.resolve(commitLog.getFileName().toString() + "." + commitLog.toFile().lastModified());
                manifest.add(new ManifestEntry(bucketKey, commitLog, ManifestEntry.Type.FILE));
            }

            logger.debug("{} files in manifest for commitlog backup.", manifest.size());

            backuper.uploadOrFreshenFiles(manifest, new OperationProgressTracker(this, manifest.size()));
        }
    }

    private Path resolveCommitLogsPath(final BackupCommitLogsOperationRequest request) {
        if (request.commitLogArchiveOverride != null && request.commitLogArchiveOverride.toFile().exists()) {
            return request.commitLogArchiveOverride;
        } else {
            return request.cassandraDirectory.resolve(CASSANDRA_COMMIT_LOGS);
        }
    }
}
