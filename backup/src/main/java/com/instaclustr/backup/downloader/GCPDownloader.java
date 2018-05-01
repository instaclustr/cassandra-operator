package com.instaclustr.backup.downloader;

import com.google.cloud.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.common.GCPRemoteObjectReference;
import com.instaclustr.backup.common.RemoteObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GCPDownloader extends Downloader {
    private static final Logger logger = LoggerFactory.getLogger(GCPDownloader.class);

    private final Storage storage;

    public GCPDownloader(final Storage storage,
                         final RestoreArguments arguments) {
        super(arguments);
        this.storage = storage;

    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        // objectKey is kept simple (e.g. "manifests/autosnap-123") so that it directly reflects the local path
        return new GCPRemoteObjectReference(objectKey, resolveRemotePath(objectKey), restoreFromBackupBucket);
    }

    @Override
    public void downloadFile(final Path localFile, final RemoteObjectReference object) throws Exception {
        final File localFilePath = localFile.toFile();
        final BlobId blobId = ((GCPRemoteObjectReference) object).blobId;

        try (final ReadChannel inputChannel = storage.reader(blobId)) {
            localFilePath.getParentFile().mkdirs();
            FileChannel fileChannel = new FileOutputStream(localFilePath).getChannel();
            ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);

            while (inputChannel.read(bytes) > 0) {
                bytes.flip();
                fileChannel.write(bytes);
                bytes.clear();
            }

            fileChannel.close();
        }
    }

    @Override
    public List<RemoteObjectReference> listFiles(final RemoteObjectReference prefix) {
        final GCPRemoteObjectReference gcpRemoteObjectReference = (GCPRemoteObjectReference) prefix;

        final List<RemoteObjectReference> fileList = new ArrayList<>();

        Page<Blob> storagePage = storage.list(gcpRemoteObjectReference.blobId.getBucket(), Storage.BlobListOption.prefix(restoreFromNodeId + "/" + gcpRemoteObjectReference.getObjectKey() + "/"), Storage.BlobListOption.currentDirectory());

        Pattern nodeIdPattern = Pattern.compile(restoreFromNodeId + "/");

        storagePage.iterateAll().forEachRemaining(blob -> {
            if (!blob.getName().endsWith("/"))
                fileList.add(objectKeyToRemoteReference(Paths.get(nodeIdPattern.matcher(blob.getName()).replaceFirst(""))));
        });

        return fileList;
    }

    @Override
    void cleanup() throws Exception {
        // Nothing to cleanup
    }
}
