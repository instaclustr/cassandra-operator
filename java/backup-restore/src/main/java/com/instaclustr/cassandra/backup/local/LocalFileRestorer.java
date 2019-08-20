package com.instaclustr.cassandra.backup.local;

import static java.util.stream.Collectors.toList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.restore.RestoreCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;

public class LocalFileRestorer extends Restorer {

    @AssistedInject
    public LocalFileRestorer(final ExecutorServiceSupplier executorServiceSupplier,
                             @Assisted final RestoreOperationRequest request) {
        super(request, executorServiceSupplier);
    }

    @AssistedInject
    public LocalFileRestorer(final ExecutorServiceSupplier executorServiceSupplier,
                             @Assisted final RestoreCommitLogsOperationRequest request) {
        super(request, executorServiceSupplier);
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception {
        return new LocalFileObjectReference(objectKey, resolveRemotePath(objectKey));
    }

    @Override
    public void downloadFile(final Path localFilePath, final RemoteObjectReference objectReference) throws Exception {
        final Path remoteFilePath = request.storageLocation.fileBackupDirectory
                .resolve(request.storageLocation.bucket)
                .resolve(Paths.get(((LocalFileObjectReference) objectReference).canonicalPath));

        //Assume that any path passed in to this function is a file
        Files.createDirectories(localFilePath.getParent());

        Files.copy(remoteFilePath, localFilePath);
    }

    @Override
    public void consumeFiles(final RemoteObjectReference prefix, final Consumer<RemoteObjectReference> consumer) throws Exception {

        final Path directoryToWalk = request.storageLocation.fileBackupDirectory.resolve(request.storageLocation.bucket).resolve(prefix.canonicalPath);

        if (!Files.exists(directoryToWalk)) {
            return;
        }

        final List<Path> pathsList = Files.walk(directoryToWalk)
                .filter(filePath -> Files.isRegularFile(filePath))
                .collect(toList());

        for (final Path path : pathsList) {
            consumer.accept(objectKeyToRemoteReference(request.storageLocation.fileBackupDirectory.resolve(request.storageLocation.nodeId).relativize(path)));
        }
    }

    @Override
    public void cleanup() {
        // Nothing to cleanup
    }
}
