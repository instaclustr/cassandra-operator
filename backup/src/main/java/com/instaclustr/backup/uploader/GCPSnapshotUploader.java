package com.instaclustr.backup.uploader;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;


public class GCPSnapshotUploader extends SnapshotUploader{
    private static final Logger logger = LoggerFactory.getLogger(GCPSnapshotUploader.class);

    private final String backupID;
    private final Storage storage;
    private final String bucket;

    @Inject
    public GCPSnapshotUploader(final String backupID,
                               final String clusterId,
                               final String bucket) {
        this.backupID = clusterId + "/" + backupID;
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucket = bucket;
    }

    static class GCPRemoteObjectReference implements RemoteObjectReference {
        final BlobId blobId;

        GCPRemoteObjectReference(final BlobId blobId) {
            this.blobId = blobId;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        final BlobId blobId = BlobId.of(bucket, Paths.get(backupID).resolve(objectKey).toString());

        return new GCPRemoteObjectReference(blobId);
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
                    .build()
            );

            return FreshenResult.FRESHENED;

        } catch (final StorageException e) {
            if (e.getCode() != 404)
                throw e;

            return FreshenResult.UPLOAD_REQUIRED;
        }
    }

    @Override
    public void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        final BlobId blobId = ((GCPRemoteObjectReference) object).blobId;

        try (final WriteChannel outputChannel = storage.writer(BlobInfo.newBuilder(blobId).build(), Storage.BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.BUCKET_OWNER_FULL_CONTROL));
             final ReadableByteChannel inputChannel = Channels.newChannel(localFileStream)) {

            ByteStreams.copy(inputChannel, outputChannel);
        }
    }

    @Override
    public void cleanup() throws Exception {
    }
}
