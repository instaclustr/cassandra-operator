package com.instaclustr.cassandra.backup.gcp;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.common.io.ByteStreams;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.gcp.GCPModule.StorageProvider;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.backup.BackupCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;

public class GCPBackuper extends Backuper {

    private final Storage storage;

    @AssistedInject
    public GCPBackuper(final StorageProvider storage,
                       final ExecutorServiceSupplier executorServiceSupplier,
                       @Assisted final BackupOperationRequest backupOperationRequest) {
        super(backupOperationRequest, executorServiceSupplier);
        this.storage = storage.get();
    }

    @AssistedInject
    public GCPBackuper(final StorageProvider storage,
                       final ExecutorServiceSupplier executorServiceSupplier,
                       @Assisted final BackupCommitLogsOperationRequest backupOperationRequest) {
        super(backupOperationRequest, executorServiceSupplier);
        this.storage = storage.get();
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        return new GCPRemoteObjectReference(objectKey, resolveRemotePath(objectKey), request.storageLocation.bucket);
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception {
        final BlobId blobId = ((GCPRemoteObjectReference) object).blobId;

        try {
            storage.copy(new Storage.CopyRequest.Builder()
                                 .setSource(blobId)
                                 .setTarget(BlobInfo.newBuilder(blobId).build(),
                                            Storage.BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.BUCKET_OWNER_FULL_CONTROL)
                                 )
                                 .build());

            return FreshenResult.FRESHENED;
        } catch (final StorageException e) {
            if (e.getCode() != 404)
                throw e;

            return FreshenResult.UPLOAD_REQUIRED;
        }
    }

    @Override
    public void uploadFile(final long size,
                           final InputStream localFileStream,
                           final RemoteObjectReference object,
                           final OperationProgressTracker operationProgressTracker) throws Exception {
        final BlobId blobId = ((GCPRemoteObjectReference) object).blobId;

        try (final WriteChannel outputChannel = storage.writer(BlobInfo.newBuilder(blobId).build(),
                                                               Storage.BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.BUCKET_OWNER_FULL_CONTROL));
             final ReadableByteChannel inputChannel = Channels.newChannel(localFileStream)) {
            ByteStreams.copy(inputChannel, outputChannel);
        } finally {
            operationProgressTracker.update();
        }
    }

    @Override
    public void cleanup() throws Exception {
    }
}
