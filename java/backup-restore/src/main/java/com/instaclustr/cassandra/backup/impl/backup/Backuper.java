package com.instaclustr.cassandra.backup.impl.backup;

import static java.nio.file.StandardOpenOption.READ;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.RateLimiter;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.OperationProgressTracker;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.StorageInteractor;
import com.instaclustr.io.RateLimitedInputStream;
import com.instaclustr.io.SeekableByteChannelInputStream;
import com.instaclustr.measure.DataRate;
import com.instaclustr.measure.DataSize;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Backuper extends StorageInteractor {
    private static final Logger logger = LoggerFactory.getLogger(Backuper.class);

    protected final BackupOperationRequest request;
    private final ExecutorServiceSupplier executorServiceSupplier;

    protected Backuper(final BackupOperationRequest request,
                       final ExecutorServiceSupplier executorServiceSupplier) {
        super(request.storageLocation);
        this.request = request;
        this.executorServiceSupplier = executorServiceSupplier;
    }

    public enum FreshenResult {
        FRESHENED,
        UPLOAD_REQUIRED
    }

    public abstract FreshenResult freshenRemoteObject(final RemoteObjectReference object) throws Exception;

    public abstract void uploadFile(final long size,
                                    final InputStream localFileStream,
                                    final RemoteObjectReference object,
                                    final OperationProgressTracker operationProgressTracker) throws Exception;

    public void uploadOrFreshenFiles(final Collection<ManifestEntry> manifest,
                                     final OperationProgressTracker operationProgressTracker) throws Exception {
        if (manifest.isEmpty()) {
            logger.info("0 files to upload.");
            return;
        }

        final long filesSizeSum = getFilesSizeSum(manifest);

        computeBPS(request, filesSizeSum);

        logger.info("{} files to upload. Total size {}.", manifest.size(), DataSize.bytesToHumanReadable(filesSizeSum));

        final CountDownLatch completionLatch = new CountDownLatch(manifest.size() - 1); // latch to prevent the manifest from being uploaded until the very end

        final ExecutorService executorService = executorServiceSupplier.get(request.concurrentConnections);

        final Iterable<Future<?>> uploadResults = manifest.stream().map((manifestEntry) -> {
            try {
                return executorService.submit(() -> {
                    try (final InputStream s = new SeekableByteChannelInputStream(FileChannel.open(manifestEntry.localFile, READ))) {

                        if (manifestEntry.type == ManifestEntry.Type.MANIFEST_FILE)
                            completionLatch.await();

                        final RemoteObjectReference remoteObjectReference = objectKeyToRemoteReference(manifestEntry.objectKey);

                        try {
                            if (freshenRemoteObject(remoteObjectReference) == Backuper.FreshenResult.FRESHENED)
                                return null; // file is fresh, skip upload

                        } catch (final InterruptedException e) {
                            throw e;
                        } catch (final Exception e) {
                            logger.warn("Failed to freshen file \"{}\".", manifestEntry.objectKey, e);
                        }

                        final InputStream rateLimitedStream = getUploadingInputStreamFunction().apply(s);

                        logger.debug("Uploading file \"{}\" ({}). {} files to go.",
                                     manifestEntry.objectKey,
                                     DataSize.bytesToHumanReadable(manifestEntry.size),
                                     completionLatch.getCount());

                        uploadFile(manifestEntry.size, rateLimitedStream, remoteObjectReference, operationProgressTracker);

                        return null;
                    } catch (final Throwable t) {
                        logger.error("Failed to upload file \"{}\".", manifestEntry.objectKey, t);

                        executorService.shutdownNow(); // prevent new tasks or other tasks from running

                        throw t;
                    } finally {
                        completionLatch.countDown();
                        operationProgressTracker.update();
                    }
                });

            } catch (final RejectedExecutionException e) {
                return Futures.immediateFailedFuture(e);
            }
        }).collect(toList());

        // wait for downloads to finish
        executorService.shutdown();

        while (true) {
            if (executorService.awaitTermination(1, TimeUnit.MINUTES))
                break;
        }

        // rethrow any exception caused by a download task so we exit with failure
        for (final Future<?> result : uploadResults) {
            result.get();
        }
    }

    private long getFilesSizeSum(final Collection<ManifestEntry> manifestEntries) {
        return manifestEntries.stream().map(e -> e.size).reduce(0L, Long::sum);
    }

    private void computeBPS(final BackupOperationRequest request, final long filesSizeSum) {
        if (request.duration != null) {
            long bps = filesSizeSum / request.duration.asSeconds().value;
            if (request.bandwidth != null)
                bps = Math.min(request.bandwidth.asBytesPerSecond().value, bps);

            bps = Math.max(new DataRate(500L, DataRate.DataRateUnit.KBPS).asBytesPerSecond().value, bps);

            request.bandwidth = new DataRate(bps, DataRate.DataRateUnit.BPS);
        }
    }

    private Function<InputStream, InputStream> getUploadingInputStreamFunction() {
        return request.bandwidth == null ? identity() : inputStream -> {
            final RateLimiter rateLimiter = RateLimiter.create(request.bandwidth.asBytesPerSecond().value);
            logger.info("Upload bandwidth capped at {}.", request.bandwidth);
            return new RateLimitedInputStream(inputStream, rateLimiter);
        };
    }
}
