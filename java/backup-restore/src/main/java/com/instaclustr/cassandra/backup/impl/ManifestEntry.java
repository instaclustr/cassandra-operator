package com.instaclustr.cassandra.backup.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.MoreObjects;

public class ManifestEntry {
    public enum Type {
        FILE,
        MANIFEST_FILE
    }

    public final Path objectKey, localFile;
    public final long size;
    public final Type type;

    public ManifestEntry(final Path objectKey,
                         final Path localFile,
                         final Type type) throws IOException {
        this.objectKey = objectKey;
        this.localFile = localFile;
        this.size = Files.size(localFile);
        this.type = type;
    }

    public ManifestEntry(final Path objectKey,
                         final Path localFile,
                         final Type type,
                         final long size) {
        this.objectKey = objectKey;
        this.localFile = localFile;
        this.size = size;
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("objectKey", objectKey.toAbsolutePath())
                .add("localFile", localFile.toAbsolutePath().toString())
                .add("type", type)
                .add("size", size)
                .toString();
    }
}
