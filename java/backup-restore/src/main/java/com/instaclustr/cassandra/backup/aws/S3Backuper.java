package com.instaclustr.cassandra.backup.aws;

import static com.amazonaws.event.ProgressEventType.TRANSFER_COMPLETED_EVENT;
import static com.amazonaws.event.ProgressEventType.TRANSFER_FAILED_EVENT;
import static java.util.Optional.ofNullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.aws.S3Module.TransferManagerProvider;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.backup.BackupCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S3Backuper extends Backuper {
    private static final Logger logger = LoggerFactory.getLogger(S3Backuper.class);

    private final TransferManager transferManager;

    private final Optional<String> kmsId;

    @AssistedInject
    public S3Backuper(
            final TransferManagerProvider transferManagerProvider,
            final ExecutorServiceSupplier executorSupplier,
            @Assisted final BackupOperationRequest request) {
        super(request, executorSupplier);
        this.transferManager = transferManagerProvider.get();
        this.kmsId = Optional.empty();
    }

    @AssistedInject
    public S3Backuper(
            final TransferManagerProvider transferManagerProvider,
            final ExecutorServiceSupplier executorServiceSupplier,
            @Assisted final BackupCommitLogsOperationRequest request) {
        super(request, executorServiceSupplier);
        this.transferManager = transferManagerProvider.get();
        this.kmsId = Optional.empty();
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        return new S3RemoteObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws InterruptedException {
        final String canonicalPath = ((S3RemoteObjectReference) object).canonicalPath;

        final CopyObjectRequest copyRequest = new CopyObjectRequest(request.storageLocation.bucket,
                                                                    canonicalPath,
                                                                    request.storageLocation.bucket,
                                                                    canonicalPath).withStorageClass(StorageClass.Standard);

        if (kmsId.isPresent()) {
            final SSEAwsKeyManagementParams params = new SSEAwsKeyManagementParams(kmsId.get());
            copyRequest.withSSEAwsKeyManagementParams(params);
        }

        try {
            // attempt to refresh existing object in the bucket via an inplace copy
            transferManager.copy(copyRequest).waitForCompletion();
            return FreshenResult.FRESHENED;

        } catch (final AmazonServiceException e) {
            // AWS S3 under certain access policies can't return NoSuchKey (404)
            // instead, it returns AccessDenied (403) — handle it the same way
            if (e.getStatusCode() != 404 && e.getStatusCode() != 403) {
                throw e;
            }

            // the freshen failed because the file/key didn't exist
            return FreshenResult.UPLOAD_REQUIRED;
        }
    }

    @Override
    public void uploadFile(
            final long size,
            final InputStream localFileStream,
            final RemoteObjectReference object,
            final OperationProgressTracker operationProgressTracker) throws Exception {
        final S3RemoteObjectReference s3RemoteObjectReference = (S3RemoteObjectReference) object;

        final PutObjectRequest putObjectRequest = new PutObjectRequest(request.storageLocation.bucket,
                                                                       s3RemoteObjectReference.canonicalPath,
                                                                       localFileStream,
                                                                       new ObjectMetadata() {{
                                                                           setContentLength(size);
                                                                       }}
        );

        if (kmsId.isPresent()) {
            final SSEAwsKeyManagementParams params = new SSEAwsKeyManagementParams(kmsId.get());
            putObjectRequest.withSSEAwsKeyManagementParams(params);
        }

        final UploadProgressListener listener = new UploadProgressListener(s3RemoteObjectReference);

        final Optional<AmazonClientException> exception = ofNullable(transferManager.upload(putObjectRequest, listener).waitForException());

        operationProgressTracker.update();

        if (exception.isPresent()) {
            throw exception.get();
        }
    }

    private static class UploadProgressListener implements S3ProgressListener {
        private final S3RemoteObjectReference s3RemoteObjectReference;

        UploadProgressListener(final S3RemoteObjectReference s3RemoteObjectReference) {
            this.s3RemoteObjectReference = s3RemoteObjectReference;
        }

        @Override
        public void progressChanged(final ProgressEvent progressEvent) {
            final ProgressEventType progressEventType = progressEvent.getEventType();

            if (progressEventType == ProgressEventType.TRANSFER_PART_COMPLETED_EVENT) {
                logger.debug("Successfully uploaded part for {}.", s3RemoteObjectReference.canonicalPath);
            }

            if (progressEventType == ProgressEventType.TRANSFER_PART_FAILED_EVENT) {
                logger.debug("Failed to upload part for {}.", s3RemoteObjectReference.canonicalPath);
            }

            if (progressEventType == TRANSFER_FAILED_EVENT) {
                logger.debug("Failed to upload {}.", s3RemoteObjectReference.canonicalPath);
            }

            if (progressEventType == TRANSFER_COMPLETED_EVENT) {
                logger.debug("Successfully uploaded {}.", s3RemoteObjectReference.canonicalPath);
            }
        }

        @Override
        public void onPersistableTransfer(final PersistableTransfer persistableTransfer) {
            // We don't resume uploads
        }
    }

    @Override
    public void cleanup() {
        try {
            // TODO cleanupMultipartUploads gets access denied, INS-2326 is meant to fix this
            cleanupMultipartUploads();
        } catch (final Exception e) {
            logger.warn("Failed to cleanup multipart uploads.", e);
        }

        try {
            transferManager.shutdownNow(true);
        } catch (final Exception ex) {
            logger.warn("Exception occurred while shutting down transfer manager for S3Backuper", ex);
        }
    }

    private void cleanupMultipartUploads() {
        final AmazonS3 s3Client = transferManager.getAmazonS3Client();

        final Instant yesterdayInstant = ZonedDateTime.now().minusDays(1).toInstant();

        logger.info("Cleaning up multipart uploads older than {}.", yesterdayInstant);

        final ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(request.storageLocation.bucket)
                .withPrefix(request.storageLocation.clusterId);

        while (true) {
            final MultipartUploadListing multipartUploadListing = s3Client.listMultipartUploads(listMultipartUploadsRequest);

            multipartUploadListing.getMultipartUploads().stream()
                                  .filter(u -> u.getInitiated().toInstant().isBefore(yesterdayInstant))
                                  .forEach(u -> {
                                      logger.info("Aborting multi-part upload for key \"{}\" initiated on {}", u.getKey(), u.getInitiated().toInstant());

                                      try {
                                          s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(request.storageLocation.bucket, u.getKey(), u.getUploadId()));

                                      } catch (final AmazonClientException e) {
                                          logger.error("Failed to abort multipart upload for key \"{}\".", u.getKey(), e);
                                      }
                                  });

            if (!multipartUploadListing.isTruncated())
                break;

            listMultipartUploadsRequest
                    .withKeyMarker(multipartUploadListing.getKeyMarker())
                    .withUploadIdMarker(multipartUploadListing.getUploadIdMarker());
        }
    }
}
