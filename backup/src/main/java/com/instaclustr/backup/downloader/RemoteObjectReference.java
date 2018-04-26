package com.instaclustr.backup.downloader;

import java.nio.file.Path;

public abstract class RemoteObjectReference {
    protected final Path objectKey;

    public RemoteObjectReference(final Path objectKey) {
        this.objectKey = objectKey;
    }

    public abstract Path getObjectKey();
}
