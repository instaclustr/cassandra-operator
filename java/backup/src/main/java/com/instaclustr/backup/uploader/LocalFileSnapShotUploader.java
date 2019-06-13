package com.instaclustr.backup.uploader;

import com.instaclustr.backup.model.BackupArguments;
import com.instaclustr.backup.common.LocalFileObjectReference;
import com.instaclustr.backup.common.RemoteObjectReference;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalFileSnapShotUploader extends SnapshotUploader {

    private final Path backupRoot;

    public LocalFileSnapShotUploader (final BackupArguments backupArguments) {
        super(backupArguments.clusterId, backupArguments.backupId, backupArguments.backupBucket);
        this.backupRoot = backupArguments.fileBackupDirectory;
    }

    private Path resolveFullRemoteObjectPath(final RemoteObjectReference objectReference) {
        return backupRoot.resolve(objectReference.canonicalPath);
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        return new LocalFileObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception {
        final File fullRemoteObject = resolveFullRemoteObjectPath(object).toFile();
        if (fullRemoteObject.exists()) {
            //if we can't update modified time for whatever reason, then we will re-upload
            if (fullRemoteObject.setLastModified(System.currentTimeMillis())) {
                return FreshenResult.FRESHENED;
            }
        }
        return FreshenResult.UPLOAD_REQUIRED;
    }

    @Override
    public void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        Path snapshotPath = resolveFullRemoteObjectPath(object);
        Files.createDirectories(snapshotPath.getParent());
        Files.copy(localFileStream, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    void cleanup() throws Exception {
        //No clean up required
    }
}
