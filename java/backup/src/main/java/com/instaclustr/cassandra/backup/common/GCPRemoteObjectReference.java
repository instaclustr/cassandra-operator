package com.instaclustr.cassandra.backup.common;

import com.google.cloud.storage.BlobId;

import java.nio.file.Path;

public class GCPRemoteObjectReference extends RemoteObjectReference {
    public final BlobId blobId;

    public GCPRemoteObjectReference(final Path objectKey, final String canonicalPath, final String bucket) {
        super(objectKey, canonicalPath);
        this.blobId =  BlobId.of(bucket, canonicalPath);
    }

    public Path getObjectKey() {
        return objectKey;
    }
}
