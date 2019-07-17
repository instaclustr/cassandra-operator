package com.instaclustr.cassandra.backup.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class StorageInteractor implements AutoCloseable {

    private final StorageLocation storageLocation;

    public abstract RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception;

    public StorageInteractor(final StorageLocation storageLocation) {
        this.storageLocation = storageLocation;
    }

    public String resolveRemotePath(final Path objectKey) {
        return Paths.get(storageLocation.clusterId).resolve(storageLocation.nodeId).resolve(objectKey).toString();
    }

    protected abstract void cleanup() throws Exception;

    private boolean isClosed = false;

    public void close() throws IOException {
        if (isClosed)
            return;

        try {
            cleanup();

            isClosed = true;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }
}
