package com.instaclustr.backup.common;

import java.nio.file.Path;

public abstract class RemoteObjectReference {
    public Path objectKey;
    public String canonicalPath;

    public RemoteObjectReference(final Path objectKey, final String canonicalPath) {
        this.objectKey = objectKey;
        this.canonicalPath = canonicalPath;
    }

    public abstract Path getObjectKey();
}
