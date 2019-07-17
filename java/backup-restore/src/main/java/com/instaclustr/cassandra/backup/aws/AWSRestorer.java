package com.instaclustr.cassandra.backup.aws;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.aws.AWSModule.TransferManagerProvider;
import com.instaclustr.threading.Executors;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWSRestorer extends Restorer {
    private static final Logger logger = LoggerFactory.getLogger(AWSRestorer.class);

    private final AmazonS3 amazonS3;
    private final TransferManager transferManager;

    @Inject
    public AWSRestorer(final TransferManagerProvider transferManagerProvider,
                       final Executors.ExecutorServiceSupplier executorServiceSupplier,
                       @Assisted final RestoreOperationRequest request) {
        super(request, executorServiceSupplier);
        this.transferManager = transferManagerProvider.get();
        this.amazonS3 = this.transferManager.getAmazonS3Client();
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        return new AWSRemoteObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public void downloadFile(final Path localPath, final RemoteObjectReference object) throws Exception {
        final GetObjectRequest getObjectRequest = new GetObjectRequest(request.storageLocation.bucket, object.canonicalPath);

        S3ProgressListener s3ProgressListener = new S3ProgressListener() {
            @Override
            public void onPersistableTransfer(final PersistableTransfer persistableTransfer) {
                // We don't resume downloads
            }

            @Override
            public void progressChanged(final ProgressEvent progressEvent) {
                if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                    logger.debug("Successfully downloaded {}.", object.canonicalPath);
                }
            }
        };

        Files.createDirectories(localPath.getParent());
        transferManager.download(getObjectRequest, localPath.toFile(), s3ProgressListener).waitForCompletion();
    }

    @Override
    public void consumeFiles(final RemoteObjectReference prefix, final Consumer<RemoteObjectReference> consumer) {

        final Path bucketPath = Paths.get(request.storageLocation.clusterId).resolve(request.storageLocation.nodeId);

        ObjectListing objectListing = amazonS3.listObjects(request.storageLocation.bucket, prefix.canonicalPath);

        boolean hasMoreContent = true;

        while (hasMoreContent) {
            objectListing.getObjectSummaries().stream()
                    .filter(objectSummary -> !objectSummary.getKey().endsWith("/"))
                    .forEach(objectSummary -> consumer.accept(objectKeyToRemoteReference(bucketPath.relativize(Paths.get(objectSummary.getKey())))));

            if (objectListing.isTruncated()) {
                objectListing = amazonS3.listNextBatchOfObjects(objectListing);
            } else {
                hasMoreContent = false;
            }
        }
    }

    @Override
    public void cleanup() {
        // Nothing to cleanup
    }
}
