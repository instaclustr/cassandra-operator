package com.instaclustr.cassandra.backup.local;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.backup.BackupCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;
import com.instaclustr.threading.Executors;

public class LocalFileBackuper extends Backuper {

    @AssistedInject
    public LocalFileBackuper(final Executors.ExecutorServiceSupplier executorServiceSupplier,
                             @Assisted final BackupOperationRequest request) {
        super(request, executorServiceSupplier);
    }

    @AssistedInject
    public LocalFileBackuper(final Executors.ExecutorServiceSupplier executorServiceSupplier,
                             @Assisted final BackupCommitLogsOperationRequest request) {
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
    public void uploadFile(final long size,
                           final InputStream localFileStream,
                           final RemoteObjectReference object,
                           final OperationProgressTracker operationProgressTracker) throws Exception {
        try {
            Path snapshotPath = resolveFullRemoteObjectPath(object);
            Files.createDirectories(snapshotPath.getParent());
            Files.copy(localFileStream, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            operationProgressTracker.update();
        }
    }

    @Override
    public void cleanup() throws Exception {
        //No clean up required
    }
}
