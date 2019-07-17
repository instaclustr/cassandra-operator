package com.instaclustr.cassandra.backup.azure;

import java.nio.file.Path;

import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

public class AzureRemoteObjectReference extends RemoteObjectReference {
    public final CloudBlockBlob blob;

    public AzureRemoteObjectReference(final Path objectKey,
                                      final String canonicalPath,
                                      final CloudBlockBlob blob) {
        super(objectKey, canonicalPath);
        this.blob = blob;
    }

    public Path getObjectKey() {
        return objectKey;
    }
}
