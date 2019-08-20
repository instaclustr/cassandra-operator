package com.instaclustr.cassandra.backup.aws;

import java.nio.file.Path;

import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;

public class S3RemoteObjectReference extends RemoteObjectReference {
    public S3RemoteObjectReference(Path objectKey, String canonicalPath) {
        super(objectKey, canonicalPath);
    }

    @Override
    public Path getObjectKey() {
        return objectKey;
    }
}
