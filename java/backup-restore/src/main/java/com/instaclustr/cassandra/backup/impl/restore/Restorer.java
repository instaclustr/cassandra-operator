package com.instaclustr.cassandra.backup.impl.restore;

import static com.instaclustr.cassandra.backup.impl.restore.Restorer.CompareFilesResult.DOWNLOAD_REQUIRED;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import com.google.common.util.concurrent.Futures;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.StorageInteractor;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Restorer extends StorageInteractor {
    private static final Logger logger = LoggerFactory.getLogger(Restorer.class);

    protected final BaseRestoreOperationRequest request;
    private final ExecutorServiceSupplier executorServiceSupplier;

    public Restorer(final BaseRestoreOperationRequest request,
                    final ExecutorServiceSupplier executorServiceSupplier) {
        super(request.storageLocation);
        this.request = request;
        this.executorServiceSupplier = executorServiceSupplier;

    }

    public enum CompareFilesResult {
        MATCHING,
        DOWNLOAD_REQUIRED
    }

    public CompareFilesResult compareRemoteObject(final long size, final Path localFilePath, final RemoteObjectReference object) throws Exception {
        if ((!localFilePath.toFile().exists()) || (Files.size(localFilePath) != size))
            return DOWNLOAD_REQUIRED;

        return CompareFilesResult.MATCHING;
    }

    public abstract void downloadFile(final Path localPath, final RemoteObjectReference objectReference) throws Exception;

    public abstract void consumeFiles(final RemoteObjectReference prefix, final Consumer<RemoteObjectReference> consumer) throws Exception;

    public void downloadFiles(final Collection<ManifestEntry> manifest) throws Exception {
        logger.info("{} files to download.", manifest.size());

        final CountDownLatch completionLatch = new CountDownLatch(manifest.size());

        final ExecutorService executorService = executorServiceSupplier.get(request.concurrentConnections);

        final Iterable<Future<?>> downloadResults = manifest.stream().map((entry) -> {
            try {
                return executorService.submit(() -> {
                    RemoteObjectReference remoteObjectReference = objectKeyToRemoteReference(entry.objectKey);
                    try {
                        logger.debug("Downloading file \"{}\" to \"{}\". {} files to go.", remoteObjectReference.getObjectKey(), entry.localFile, completionLatch.getCount());

                        this.downloadFile(entry.localFile, remoteObjectReference);

                        logger.info("Successfully downloaded file \"{}\" to \"{}\".", remoteObjectReference.getObjectKey(), entry.localFile);

                        return null;
                    } catch (final Throwable t) {
                        logger.error("Failed to download file \"{}\".", remoteObjectReference.getObjectKey(), t);

                        executorService.shutdownNow(); // prevent new tasks or other tasks from running

                        throw t;

                    } finally {
                        completionLatch.countDown();
                    }
                });
            } catch (final RejectedExecutionException e) {
                return Futures.immediateFailedFuture(e);
            }
        }).collect(toList());

        // wait for uploads to finish
        executorService.shutdown();

        while (true) {
            if (executorService.awaitTermination(1, MINUTES))
                break;
        }

        // rethrow any exception caused by a download task so we exit with failure
        for (final Future<?> result : downloadResults) {
            result.get();
        }
    }
}
