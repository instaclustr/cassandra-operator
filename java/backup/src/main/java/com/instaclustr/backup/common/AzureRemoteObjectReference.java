package com.instaclustr.backup.common;

import com.instaclustr.backup.downloader.AzureDownloader;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

import java.nio.file.Path;

public class AzureRemoteObjectReference extends RemoteObjectReference {
    public final CloudBlockBlob blob;

    public AzureRemoteObjectReference(final Path objectKey, final String canonicalPath, final CloudBlockBlob blob) {
        super(objectKey, canonicalPath);
        this.blob = blob;
    }

    public Path getObjectKey() {
        return objectKey;
    }
}
