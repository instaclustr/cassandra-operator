package com.instaclustr.cassandra.backup.util;


import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class GlobalLock {
    private Path snapshotLocksDirectory;

    public GlobalLock(final String sharedContainerRoot) throws IOException {
        this.snapshotLocksDirectory = Paths.get(sharedContainerRoot).resolve(Paths.get("var/lock/node-agent"));

        Files.createDirectories(snapshotLocksDirectory);
    }

    public boolean getLock(final boolean waitForLock) throws IOException {
        final Path globalLockFile = this.snapshotLocksDirectory.resolve("global-transfer-lock");

        // attempt to acquire the global lock -- prevents concurrent uploads/downloads across processes
        try (final FileChannel globalLockChannel = FileChannel.open(globalLockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
             final FileLock globalLock = (waitForLock ? globalLockChannel.lock() : globalLockChannel.tryLock())) {
            if (globalLock == null) {
                throw new RuntimeException(String.format("Unable to acquire global transfer lock (%s). Is another com.instaclustr.backup or restore running?", globalLockFile));
            }

            return true;
        }
    }
}
