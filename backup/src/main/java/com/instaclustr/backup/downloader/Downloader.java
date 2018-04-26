package com.instaclustr.backup.downloader;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import com.instaclustr.backup.task.ManifestEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public abstract class Downloader implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);

    public abstract RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception;

    public enum CompareFilesResult {
        MATCHING,
        DOWNLOAD_REQUIRED
    }

    public CompareFilesResult compareRemoteObject(final long size, final Path localFilePath, final RemoteObjectReference object) throws Exception {
        if ((!localFilePath.toFile().exists()) || (Files.size(localFilePath) != size))
            return CompareFilesResult.DOWNLOAD_REQUIRED;

        return CompareFilesResult.MATCHING;
    }

    public abstract void downloadFile(final Path localPath, final RemoteObjectReference object) throws Exception;

    public abstract List<RemoteObjectReference> listFiles(final RemoteObjectReference prefix) throws Exception;

    public void downloadFileToFolder(final Path localDestinationDirectory, final RemoteObjectReference object) throws Exception {
        downloadFile(localDestinationDirectory.resolve(object.getObjectKey().getFileName()), object);
    }

    public void downloadFiles(final Collection<ManifestEntry> manifest, final String eventType, final int concurrentDownloads) throws Exception {
        final RateLimiter objectRateLimiter = RateLimiter.create(10);

        logger.info("{} files to download.", manifest.size());

            // create a ThreadPoolExecutor with a fixed size queue RejectedExecutionHandler that retries queue.offer() as queue.put() (blocking)
            final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(concurrentDownloads));

            final CountDownLatch completionLatch = new CountDownLatch(manifest.size());

            final Iterable<Future<?>> downloadResults = manifest.stream().map((entry) -> {
                try {
                    return executorService.submit(() -> {
                        RemoteObjectReference remoteObjectReference = objectKeyToRemoteReference(entry.objectKey);
                        try {
                            logger.debug("Downloading file \"{}\" to \"{}\". {} files to go.", remoteObjectReference.getObjectKey(), entry.localFile, completionLatch.getCount());

                            try {
                                objectRateLimiter.acquire();
                            } catch (final Exception e) {
                                logger.warn("Failed to get rate limiter lock for file \"{}\".", remoteObjectReference.getObjectKey(), e);
                            }

                            this.downloadFile(entry.localFile, remoteObjectReference);

                            logger.info("Successfully downloaded file \"{}\".", remoteObjectReference.getObjectKey());

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
            }).collect(Collectors.toList());

            // wait for downloads to finish
            executorService.shutdown();
            while (true) {
                if (executorService.awaitTermination(1, TimeUnit.MINUTES))
                    break;
            }

            // rethrow any exception caused by a download task so we exit with failure
            for (final Future<?> result : downloadResults) {
                result.get();
            }
    }

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
