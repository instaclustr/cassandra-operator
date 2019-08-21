package com.instaclustr.cassandra.backup.aws;

import static java.util.Optional.ofNullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.aws.S3Module.TransferManagerProvider;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.restore.RestoreCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S3Restorer extends Restorer {
    private static final Logger logger = LoggerFactory.getLogger(S3Restorer.class);

    private final AmazonS3 amazonS3;
    private final TransferManager transferManager;

    @AssistedInject
    public S3Restorer(final TransferManagerProvider transferManagerProvider,
                      final ExecutorServiceSupplier executorServiceSupplier,
                      @Assisted final RestoreOperationRequest request) {
        super(request, executorServiceSupplier);
        this.transferManager = transferManagerProvider.get();
        this.amazonS3 = this.transferManager.getAmazonS3Client();
    }

    @AssistedInject
    public S3Restorer(final TransferManagerProvider transferManagerProvider,
                      final ExecutorServiceSupplier executorServiceSupplier,
                      @Assisted final RestoreCommitLogsOperationRequest request) {
        super(request, executorServiceSupplier);
        this.transferManager = transferManagerProvider.get();
        this.amazonS3 = this.transferManager.getAmazonS3Client();
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        return new S3RemoteObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public void downloadFile(final Path localPath, final RemoteObjectReference objectReference) throws Exception {
        final GetObjectRequest getObjectRequest = new GetObjectRequest(request.storageLocation.bucket, objectReference.canonicalPath);

        Files.createDirectories(localPath.getParent());

        final Optional<AmazonClientException> exception = ofNullable(transferManager.download(getObjectRequest,
                                                                                              localPath.toFile(),
                                                                                              new DownloadProgressListener(objectReference)).waitForException());

        if (exception.isPresent()) {
            if (exception.get() instanceof AmazonS3Exception && ((AmazonS3Exception) exception.get()).getStatusCode() == 404) {
                logger.error("Remote object reference {} does not exist.", objectReference);
            }

            throw exception.get();
        }
    }

    private static class DownloadProgressListener implements S3ProgressListener {
        private final RemoteObjectReference objectReference;

        public DownloadProgressListener(final RemoteObjectReference objectReference) {
            this.objectReference = objectReference;
        }

        @Override
        public void onPersistableTransfer(final PersistableTransfer persistableTransfer) {
            // We don't resume downloads
        }

        @Override
        public void progressChanged(final ProgressEvent progressEvent) {
            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                logger.debug("Successfully downloaded {}.", objectReference.canonicalPath);
            }
        }
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
        transferManager.shutdownNow();
    }
}
