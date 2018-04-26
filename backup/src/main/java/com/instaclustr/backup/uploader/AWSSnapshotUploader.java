package com.instaclustr.backup.uploader;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;

public class AWSSnapshotUploader extends SnapshotUploader {
    private static final Logger logger = LoggerFactory.getLogger(AWSSnapshotUploader.class);

    private final TransferManager transferManager;

    private final String backupID;
    private final String clusterID;
    private final String backupBucket;

    private final Optional<String> kmsId;

    public AWSSnapshotUploader(final String backupID,
                               final String clusterID,
                               final String backupBucket,
                               final Optional<String> kmsId) {

        this.transferManager = TransferManagerBuilder.defaultTransferManager();
        this.backupID = backupID;
        this.clusterID = clusterID;
        this.backupBucket = backupBucket;
        this.kmsId = kmsId;
    }

    static class AWSRemoteObjectReference implements RemoteObjectReference {
        final String canonicalPath;

        AWSRemoteObjectReference(final String canonicalPath) {
            this.canonicalPath = canonicalPath;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        final String canonicalPath = Paths.get(clusterID).resolve(backupID).resolve(objectKey).toString();
        return new AWSRemoteObjectReference(canonicalPath);
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws InterruptedException {
        final String canonicalPath = ((AWSRemoteObjectReference) object).canonicalPath;

        final CopyObjectRequest copyRequest = new CopyObjectRequest(backupBucket, canonicalPath, backupBucket, canonicalPath)
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

        final PutObjectRequest putObjectRequest = new PutObjectRequest(backupBucket, awsRemoteObjectReference.canonicalPath, localFileStream,
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
//            cleanupMultipartUploads();

        } catch (Exception e) {
            logger.warn("Failed to cleanup multipart uploads.", e);
        }
    }

    private void cleanupMultipartUploads() {
        final AmazonS3 s3Client = transferManager.getAmazonS3Client();

        final Instant yesterdayInstant = ZonedDateTime.now().minusDays(1).toInstant();

        logger.info("Cleaning up multipart uploads older than {}.", yesterdayInstant);

        final ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(backupBucket)
                .withPrefix(clusterID);

        while (true) {
            final MultipartUploadListing multipartUploadListing = s3Client.listMultipartUploads(listMultipartUploadsRequest);

            multipartUploadListing.getMultipartUploads().stream()
                    .filter(u -> u.getInitiated().toInstant().isBefore(yesterdayInstant))
                    .forEach(u -> {
                        logger.info("Aborting multi-part upload for key \"{}\" initiated on {}", u.getKey(), u.getInitiated().toInstant());

                        try {
                            s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(backupBucket, u.getKey(), u.getUploadId()));

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
