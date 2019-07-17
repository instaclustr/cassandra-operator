package com.instaclustr.io;


import static java.nio.file.Files.createDirectories;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GlobalLock {
    private final Path snapshotLocksDirectory;

    public GlobalLock(final Path sharedContainerRoot) throws IOException {
        this.snapshotLocksDirectory = sharedContainerRoot.resolve(Paths.get("var/lock/backup-restore"));
        createDirectories(snapshotLocksDirectory);
    }

    public FileLock lock() throws Exception {
        return waitForLock(false);
    }

    public FileLock waitForLock(final boolean waitForLock) throws Exception {
        final Path globalLockFile = this.snapshotLocksDirectory.resolve("global-transfer-lock");

        // attempt to acquire the global lock -- prevents concurrent uploads/downloads across processes
        try (final FileChannel globalLockChannel = FileChannel.open(globalLockFile, WRITE, CREATE);
             final FileLock globalLock = (waitForLock ? globalLockChannel.lock() : globalLockChannel.tryLock())) {
            if (globalLock == null) {
                throw new RuntimeException(String.format("Unable to acquire global transfer lock (%s). Is another backup or restore running?", globalLockFile));
            }

            return globalLock;
        }
    }
}
