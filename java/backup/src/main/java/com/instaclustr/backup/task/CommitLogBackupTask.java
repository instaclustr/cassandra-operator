package com.instaclustr.backup.task;

import com.instaclustr.backup.model.CommitLogBackupArguments;
import com.instaclustr.backup.uploader.FilesUploader;
import com.instaclustr.backup.util.Directories;
import com.instaclustr.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class CommitLogBackupTask implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(CommitLogBackupTask.class);

    private final GlobalLock globalLock;
    private final FilesUploader filesUploader;
    private final CommitLogBackupArguments arguments;
    private final Path backupCommitLogRootKey;
    private final Path commitLogArchiveDirectory;

    public CommitLogBackupTask(final Path sharedContainerRoot,
                               final Path commitLogArchiveDirectory,
                               final GlobalLock globalLock,
                               final FilesUploader filesUploader,
                               final CommitLogBackupArguments arguments) {

        this.globalLock = globalLock;
        this.filesUploader = filesUploader;
        this.arguments = arguments;
        this.backupCommitLogRootKey = Paths.get(Directories.CASSANDRA_COMMIT_LOGS);
        this.commitLogArchiveDirectory = sharedContainerRoot.resolve(arguments.commitLogArchiveOverride != null ? arguments.commitLogArchiveOverride : commitLogArchiveDirectory);
    }

    @Override
    public Void call() throws Exception {
        if (globalLock.getLock(arguments.waitForLock)) {
            try {
                call0();
            } catch (Exception e) {
                logger.error("Error uploading commitlogs.", e);
            }
        }

        return null;
    }

    protected void call0() throws Exception {
        // generate manifest (set of object keys and source files defining the upload)
        final Collection<ManifestEntry> manifest = new LinkedList<>(); // linked list to maintain order
        final Pattern pattern = Pattern.compile("CommitLog-\\d+-\\d+\\.log");
        final DirectoryStream.Filter<Path> filter = entry -> Files.isRegularFile(entry) && pattern.matcher(entry.getFileName().toString()).matches();

        try (final DirectoryStream<Path> commitLogs = Files.newDirectoryStream(commitLogArchiveDirectory, filter)) {
            for (final Path commitLog : commitLogs) {
                // Append file modified date so we have some idea of the time range this commitlog covers
                final Path bucketKey = backupCommitLogRootKey.resolve(commitLog.getFileName().toString() + "." + String.valueOf(commitLog.toFile().lastModified()));
                manifest.add(new ManifestEntry(bucketKey, commitLog, ManifestEntry.Type.FILE));
            }

            logger.debug("{} files in manifest for commitlog com.instaclustr.backup.", manifest.size());

            if (! manifest.isEmpty()) {
                filesUploader.uploadOrFreshenFiles(manifest);
            }
        }
    }
}
