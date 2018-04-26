package com.instaclustr.backup.uploader;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.EnumSet;

public class AzureSnapshotUploader extends SnapshotUploader {
    private static final Logger logger = LoggerFactory.getLogger(AzureSnapshotUploader.class);

    private static final String DATE_TIME_METADATA_KEY = "LastFreshened";

    private final String backupID;
    private final String clusterID;

    private final CloudBlobContainer blobContainer;

    public AzureSnapshotUploader(final String backupID,
                                 final String clusterID,
                                 final String backupBucket,
                                 final String azureAccountName,
                                 final String azureAccountKey) throws URISyntaxException, StorageException {
        this.backupID = backupID;
        this.clusterID = clusterID;

        //Currently just use clusterId (name) as container reference
        this.blobContainer = new CloudStorageAccount(new StorageCredentialsAccountAndKey(azureAccountName, azureAccountKey)).createCloudBlobClient().getContainerReference(backupBucket);
    }

    static class AzureRemoteObjectReference implements RemoteObjectReference {
        final CloudBlockBlob blob;

        AzureRemoteObjectReference(final CloudBlockBlob blob) {
            this.blob = blob;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        final String canonicalPath = Paths.get(clusterID).resolve(backupID).resolve(objectKey).toString();
        final CloudBlockBlob blob = this.blobContainer.getBlockBlobReference(canonicalPath);

        return new AzureRemoteObjectReference(blob);
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception {
        final CloudBlockBlob blob = ((AzureRemoteObjectReference) object).blob;

        final Instant now = Instant.now();

        try {
            blob.getMetadata().put(DATE_TIME_METADATA_KEY, now.toString());
            blob.uploadMetadata();

            return FreshenResult.FRESHENED;

        } catch (final StorageException e) {
            if (e.getHttpStatusCode() != 404)
                throw e;

            return FreshenResult.UPLOAD_REQUIRED;
        }
    }

    @Override
    public void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        final CloudBlockBlob blob = ((AzureRemoteObjectReference) object).blob;

        blob.upload(localFileStream, size);
    }

    @Override
    void cleanup() throws Exception {
        deleteStaleBlobs();
    }

    private void deleteStaleBlobs() throws StorageException, URISyntaxException {
        final Date expiryDate = Date.from(ZonedDateTime.now().minusWeeks(1).toInstant());

        final CloudBlobDirectory directoryReference = blobContainer.getDirectoryReference(clusterID);

        for (final ListBlobItem blob : directoryReference.listBlobs(null, true, EnumSet.noneOf(BlobListingDetails.class), null, null)) {
            if (!(blob instanceof CloudBlob))
                continue;

            final BlobProperties properties = ((CloudBlob) blob).getProperties();
            if (properties == null || properties.getLastModified() == null)
                continue;

            if (properties.getLastModified().before(expiryDate))
                ((CloudBlob) blob).delete();
        }
    }
}
