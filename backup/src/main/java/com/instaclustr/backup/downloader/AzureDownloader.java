package com.instaclustr.backup.downloader;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class AzureDownloader extends Downloader {
    private static final Logger logger = LoggerFactory.getLogger(AzureDownloader.class);

    private final String restoreFromClusterId;
    private final String restoreFromNodeId;
    private final CloudBlobContainer blobContainer;

    public AzureDownloader(final CloudBlobClient cloudBlobClient,
                           String restoreFromClusterId,
                           final String restoreBackupId) throws StorageException, URISyntaxException {
        this.restoreFromClusterId = restoreFromClusterId;
        this.restoreFromNodeId = restoreBackupId;
        this.blobContainer = cloudBlobClient.getContainerReference(restoreFromClusterId);
    }

    static class AzureRemoteObjectReference extends RemoteObjectReference {
        private final CloudBlockBlob blob;

        AzureRemoteObjectReference(final Path objectKey, final CloudBlockBlob blob) {
            super(objectKey);
            this.blob = blob;
        }

        public Path getObjectKey() {
            return objectKey;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws StorageException, URISyntaxException {
        final String canonicalPath = Paths.get(restoreFromClusterId).resolve(restoreFromNodeId).resolve(objectKey).toString();
        final CloudBlockBlob blob = this.blobContainer.getBlockBlobReference(canonicalPath);

        return new AzureRemoteObjectReference(objectKey, blob);
    }

    @Override
    public void downloadFile(final Path localPath, final RemoteObjectReference object) throws Exception {
        final CloudBlockBlob blob = ((AzureRemoteObjectReference) object).blob;
        final File localFilePath = localPath.toFile();
        localFilePath.getParentFile().mkdirs();
        blob.downloadToFile(localFilePath.toString());
    }

    @Override
    public List<RemoteObjectReference> listFiles(final RemoteObjectReference prefix) throws Exception {
        final AzureRemoteObjectReference azureRemoteObjectReference = (AzureRemoteObjectReference) prefix;
        final String blobPrefix = Paths.get(restoreFromClusterId).resolve(restoreFromNodeId).resolve(azureRemoteObjectReference.getObjectKey()).toString();

        List<RemoteObjectReference> fileList = new ArrayList<>();

        Pattern containerPattern = Pattern.compile("^/" + restoreFromClusterId + "/" + restoreFromClusterId + "/" + restoreFromNodeId + "/");

        Iterable<ListBlobItem> blobItemsIterable = blobContainer.listBlobs(blobPrefix, true, EnumSet.noneOf(BlobListingDetails.class), null, null);
        Iterator<ListBlobItem> blobItemsIterator = blobItemsIterable.iterator();

        while (blobItemsIterator.hasNext()) {
            ListBlobItem listBlobItem = blobItemsIterator.next();

            try {
                fileList.add(objectKeyToRemoteReference(Paths.get(containerPattern.matcher(listBlobItem.getUri().getPath()).replaceFirst(""))));
            } catch (StorageException | URISyntaxException e) {
                logger.error("Failed to generate objectKey for blob item \"{}\".", listBlobItem.getUri(), e);

                throw e;
            }
        }

        return fileList;
    }

    @Override
    void cleanup() throws Exception {
        // Nothing to cleanup
    }
}
