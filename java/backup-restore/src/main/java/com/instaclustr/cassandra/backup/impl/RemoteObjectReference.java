package com.instaclustr.cassandra.backup.impl;

import java.nio.file.Path;

import com.google.common.base.MoreObjects;

public abstract class RemoteObjectReference {
    public Path objectKey;
    public String canonicalPath;

    public RemoteObjectReference(final Path objectKey, final String canonicalPath) {
        this.objectKey = objectKey;
        this.canonicalPath = canonicalPath;
    }

    public abstract Path getObjectKey();

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("objectKey", objectKey)
                .add("canonicalPath", canonicalPath)
                .toString();
    }
}
