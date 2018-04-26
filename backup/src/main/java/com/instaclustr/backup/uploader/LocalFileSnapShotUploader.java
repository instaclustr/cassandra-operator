package com.instaclustr.backup.uploader;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileSnapShotUploader extends SnapshotUploader {

    private final Path backupRoot;

    @AssistedInject
    public LocalFileSnapShotUploader (@Assisted final Path backupRoot) {
        this.backupRoot = backupRoot;
    }

    static class LocalFileObjectReference implements RemoteObjectReference {
        private final Path objectKey;

        public LocalFileObjectReference(final Path objectKey) {
            this.objectKey = objectKey;
        }
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        return new LocalFileObjectReference(objectKey);
    }

    @Override
    public FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception {
        return FreshenResult.UPLOAD_REQUIRED;
    }

    @Override
    public void uploadSnapshotFile(final long size, final InputStream localFileStream, final RemoteObjectReference object) throws Exception {
        Path snapshotPath = backupRoot.resolve(((LocalFileObjectReference) object).objectKey);
        Files.createDirectories(snapshotPath.getParent());

        FileOutputStream snapShotOut = new FileOutputStream(snapshotPath.toFile());

        byte buffer[] = new byte[1024];
        int read = localFileStream.read(buffer);
        while (read != -1) {
            snapShotOut.write(buffer, 0, read);
            read = localFileStream.read(buffer);
        }
    }

    @Override
    void cleanup() throws Exception {
        //FIXME
    }
}
