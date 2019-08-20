package com.instaclustr.cassandra.backup.gcp;

import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.instaclustr.cassandra.backup.gcp.GCPModule.StorageProvider;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.restore.RestoreCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;

public class GCPRestorer extends Restorer {

    private final Storage storage;

    @AssistedInject
    public GCPRestorer(final StorageProvider storage,
                       final ExecutorServiceSupplier executorServiceSupplier,
                       @Assisted final RestoreOperationRequest request) {
        super(request, executorServiceSupplier);
        this.storage = storage.get();
    }

    @AssistedInject
    public GCPRestorer(final StorageProvider storage,
                       final ExecutorServiceSupplier executorServiceSupplier,
                       @Assisted final RestoreCommitLogsOperationRequest request) {
        super(request, executorServiceSupplier);
        this.storage = storage.get();
    }

    @Override
    public RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) {
        // objectKey is kept simple (e.g. "manifests/autosnap-123") so that it directly reflects the local path
        return new GCPRemoteObjectReference(objectKey, resolveRemotePath(objectKey), request.storageLocation.bucket);
    }

    @Override
    public void downloadFile(final Path localFile, final RemoteObjectReference objectReference) throws Exception {
        final BlobId blobId = ((GCPRemoteObjectReference) objectReference).blobId;
        Files.createDirectories(localFile);

        try (final ReadChannel inputChannel = storage.reader(blobId)) {
            Files.copy(Channels.newInputStream(inputChannel), localFile);
        }
    }

    @Override
    public void consumeFiles(final RemoteObjectReference prefix, final Consumer<RemoteObjectReference> consumer) {
        final GCPRemoteObjectReference gcpRemoteObjectReference = (GCPRemoteObjectReference) prefix;

        final Page<Blob> storagePage = storage.list(gcpRemoteObjectReference.blobId.getBucket(),
                                                    BlobListOption.prefix(request.storageLocation.nodeId + "/" + gcpRemoteObjectReference.getObjectKey() + "/"),
                                                    BlobListOption.currentDirectory());

        final Pattern nodeIdPattern = Pattern.compile(request.storageLocation.nodeId + "/");

        storagePage.iterateAll().iterator().forEachRemaining(blob -> {
            if (!blob.getName().endsWith("/"))
                consumer.accept(objectKeyToRemoteReference(Paths.get(nodeIdPattern.matcher(blob.getName()).replaceFirst(""))));
        });
    }

    @Override
    public void cleanup() throws Exception {
        // Nothing to cleanup
    }
}
