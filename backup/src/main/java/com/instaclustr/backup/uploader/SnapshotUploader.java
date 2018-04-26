package com.instaclustr.backup.uploader;

import java.io.InputStream;
import java.nio.file.Path;

public abstract class SnapshotUploader implements AutoCloseable {
    public abstract RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception;

    public enum FreshenResult {
        FRESHENED,
        UPLOAD_REQUIRED
    }

    public abstract FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception;

    public abstract void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception;

    abstract void cleanup() throws Exception;

    private boolean isClosed = false;

    @Override
    public final void close() throws Exception {
        if (isClosed)
            return;

        isClosed = true;
        cleanup();
    }
}
