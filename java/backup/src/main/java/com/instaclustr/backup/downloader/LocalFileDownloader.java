package com.instaclustr.backup.downloader;

import com.instaclustr.backup.model.RestoreArguments;
import com.instaclustr.backup.common.LocalFileObjectReference;
import com.instaclustr.backup.common.RemoteObjectReference;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocalFileDownloader extends Downloader {
    private final Path sourceDirectory;

    public LocalFileDownloader(final RestoreArguments arguments) {
        super(arguments);
        this.sourceDirectory = arguments.fileBackupDirectory;
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        return new LocalFileObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public void downloadFile(final Path localFilePath, final RemoteObjectReference object) throws Exception {
        Path remoteFilePath = sourceDirectory.resolve(Paths.get(((LocalFileObjectReference) object).canonicalPath));

        //Assume that any path passed in to this function is a file
        Files.createDirectories(localFilePath.getParent());

        Files.copy(remoteFilePath, localFilePath);
    }

    @Override
    public List<RemoteObjectReference> listFiles(final RemoteObjectReference prefix) throws Exception {
        try {
            final LocalFileObjectReference localFileObjectReference = (LocalFileObjectReference) prefix;

            final List<RemoteObjectReference> remoteObjectReferenceList = new ArrayList<>();

            List<Path> pathsList = Files.walk(sourceDirectory.resolve(localFileObjectReference.canonicalPath))
                    .filter(filePath -> Files.isRegularFile(filePath))
                    .collect(Collectors.toList());

            for (Path path : pathsList) {
                remoteObjectReferenceList.add(objectKeyToRemoteReference(sourceDirectory.resolve(restoreFromNodeId).relativize(path)));
            }

            return remoteObjectReferenceList;
        } catch (NoSuchFileException e) {
            return new ArrayList<>(); //No commitlog dir or files, so ignore (may need to throw exception?)
        }
    }

    @Override
    void cleanup() throws Exception {
        // Nothing to cleanup
    }
}
