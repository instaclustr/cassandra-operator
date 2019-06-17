package com.instaclustr.backup.uploader;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Optional;
import com.instaclustr.backup.model.BackupArguments;
import com.instaclustr.backup.common.RemoteObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;

public class AWSSnapshotUploader extends SnapshotUploader {
    private static final Logger logger = LoggerFactory.getLogger(AWSSnapshotUploader.class);

    private final TransferManager transferManager;

    private final Optional<String> kmsId;

    public AWSSnapshotUploader(final TransferManager transferManager,
                               final BackupArguments arguments) {
        super(arguments.clusterId, arguments.backupId, arguments.backupBucket);

        this.transferManager = transferManager;
        this.kmsId = Optional.absent();
    }

    static class AWSRemoteObjectReference extends RemoteObjectReference {
        public AWSRemoteObjectReference(Path objectKey, String canonicalPath) {
            super(objectKey, canonicalPath);
        }

        @Override
        public Path getObjectKey() {
            return objectKey;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        return new AWSRemoteObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws InterruptedException {
        final String canonicalPath = ((AWSRemoteObjectReference) object).canonicalPath;

        final CopyObjectRequest copyRequest = new CopyObjectRequest(restoreFromBackupBucket, canonicalPath, restoreFromBackupBucket, canonicalPath)
                .withStorageClass(StorageClass.Standard);

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
    public void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        final AWSRemoteObjectReference awsRemoteObjectReference = (AWSRemoteObjectReference) object;

        final PutObjectRequest putObjectRequest = new PutObjectRequest(restoreFromBackupBucket, awsRemoteObjectReference.canonicalPath, localFileStream,
                new ObjectMetadata() {{
                    setContentLength(size);
                }}
        );

        if (kmsId.isPresent()) {
            final SSEAwsKeyManagementParams params = new SSEAwsKeyManagementParams(kmsId.get());
            putObjectRequest.withSSEAwsKeyManagementParams(params);
        }

        final Upload upload = transferManager.upload(putObjectRequest);

        upload.addProgressListener((ProgressListener) progressEvent -> {
            if (progressEvent.getEventType() == ProgressEventType.TRANSFER_PART_COMPLETED_EVENT)
                logger.debug("Successfully uploaded part for {}.", awsRemoteObjectReference.canonicalPath);
        });

        upload.waitForCompletion();
    }

    @Override
    void cleanup() throws Exception {
        try {
            // TODO cleanupMultipartUploads gets access denied, INS-2326 is meant to fix this
            cleanupMultipartUploads();

        } catch (Exception e) {
            logger.warn("Failed to cleanup multipart uploads.", e);
        }
    }

    private void cleanupMultipartUploads() {
        final AmazonS3 s3Client = transferManager.getAmazonS3Client();

        final Instant yesterdayInstant = ZonedDateTime.now().minusDays(1).toInstant();

        logger.info("Cleaning up multipart uploads older than {}.", yesterdayInstant);

        final ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(restoreFromBackupBucket)
                .withPrefix(restoreFromClusterId);

        while (true) {
            final MultipartUploadListing multipartUploadListing = s3Client.listMultipartUploads(listMultipartUploadsRequest);

            multipartUploadListing.getMultipartUploads().stream()
                    .filter(u -> u.getInitiated().toInstant().isBefore(yesterdayInstant))
                    .forEach(u -> {
                        logger.info("Aborting multi-part upload for key \"{}\" initiated on {}", u.getKey(), u.getInitiated().toInstant());

                        try {
                            s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(restoreFromBackupBucket, u.getKey(), u.getUploadId()));

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
