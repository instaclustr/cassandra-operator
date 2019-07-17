package com.instaclustr.cassandra.backup.local;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.threading.Executors;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;

public class LocalFileBackuper extends Backuper {

    @Inject
    public LocalFileBackuper(final Executors.ExecutorServiceSupplier executorServiceSupplier,
                             @Assisted final BackupOperationRequest request) {
        super(request, executorServiceSupplier);
    }

    private Path resolveFullRemoteObjectPath(final RemoteObjectReference objectReference) {
        return request.storageLocation.fileBackupDirectory.resolve(request.storageLocation.bucket).resolve(objectReference.canonicalPath);
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
    public void uploadFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        Path snapshotPath = resolveFullRemoteObjectPath(object);
        Files.createDirectories(snapshotPath.getParent());
        Files.copy(localFileStream, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void cleanup() throws Exception {
        //No clean up required
    }
}
