package com.instaclustr.cassandra.backup.aws;

import java.nio.file.Path;

import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;

public class AWSRemoteObjectReference extends RemoteObjectReference {
    public AWSRemoteObjectReference(Path objectKey, String canonicalPath) {
        super(objectKey, canonicalPath);
    }

    @Override
    public Path getObjectKey() {
        return objectKey;
    }
}
