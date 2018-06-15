package com.instaclustr.backup.common;

import java.nio.file.Path;

public class LocalFileObjectReference extends RemoteObjectReference {

    public LocalFileObjectReference(final Path objectKey, final String canonicalPath) {
        super(objectKey, canonicalPath);
    }

    @Override
    public Path getObjectKey() {
        return objectKey;
    }
}
