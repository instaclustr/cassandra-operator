package com.instaclustr.backup.downloader;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.transfer.PersistableTransfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.internal.S3ProgressListener;
import com.instaclustr.backup.model.RestoreArguments;
import com.instaclustr.backup.common.RemoteObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AWSDownloader extends Downloader {
    private static final Logger logger = LoggerFactory.getLogger(AWSDownloader.class);

    private final AmazonS3 amazonS3;
    private final TransferManager transferManager;

    public AWSDownloader(final TransferManager transferManager, final RestoreArguments arguments) {
        super(arguments);
        this.amazonS3 = transferManager.getAmazonS3Client();
        this.transferManager = transferManager;
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
    public void downloadFile(final Path localPath, final RemoteObjectReference object) throws Exception {
        final GetObjectRequest getObjectRequest = new GetObjectRequest(restoreFromBackupBucket, object.canonicalPath);

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
    public List<RemoteObjectReference> listFiles(final RemoteObjectReference prefix) {
        final AWSRemoteObjectReference awsRemoteObjectReference = (AWSRemoteObjectReference) prefix;
        final Path bucketPath = Paths.get(restoreFromClusterId).resolve(restoreFromNodeId);

        List<RemoteObjectReference> fileList = new ArrayList<>();
        ObjectListing objectListing = amazonS3.listObjects(restoreFromBackupBucket, awsRemoteObjectReference.canonicalPath);

        boolean hasMoreContent = true;

        while (hasMoreContent) {
            objectListing.getObjectSummaries().stream()
                .filter(objectSummary -> !objectSummary.getKey().endsWith("/"))
                .forEach(objectSummary -> fileList.add(objectKeyToRemoteReference(bucketPath.relativize(Paths.get(objectSummary.getKey())))));

            if (objectListing.isTruncated()) {
                objectListing = amazonS3.listNextBatchOfObjects(objectListing);
            } else {
                hasMoreContent = false;
            }
        }

        return fileList;
    }

    @Override
    void cleanup() throws Exception {
        // Nothing to cleanup
    }
}
