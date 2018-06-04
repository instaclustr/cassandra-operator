package com.instaclustr.backup.uploader;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.CommonBackupArguments;
import com.instaclustr.backup.common.RemoteObjectReference;
import com.instaclustr.backup.task.ManifestEntry;
import com.instaclustr.backup.common.CloudDownloadUploadFactory;
import com.instaclustr.backup.util.DataRate;
import com.instaclustr.backup.util.DataSize;
import com.instaclustr.backup.util.SeekableByteChannelInputStream;
import com.microsoft.azure.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FilesUploader {
    private static final Logger logger = LoggerFactory.getLogger(FilesUploader.class);

    private final SnapshotUploader snapshotUploaderProvider;
    private final CommonBackupArguments arguments;
//    private final Meter uploadThroughputMeter;

    public class RateLimitedInputStream extends FilterInputStream {
        final RateLimiter limiter;

        public RateLimitedInputStream(final InputStream in, final RateLimiter limiter) {
            super(in);
            this.limiter = limiter;
        }

        @Override
        public int read() throws IOException {
            limiter.acquire();
//            uploadThroughputMeter.mark();

            return super.read();
        }

        @Override
        public int read(final byte[] b) throws IOException {
            limiter.acquire(Math.max(1, b.length));
//            uploadThroughputMeter.mark(b.length);

            return super.read(b);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            limiter.acquire(Math.max(1, len));
//            uploadThroughputMeter.mark(len);

            return super.read(b, off, len);
        }
    }

    public FilesUploader(final BackupArguments arguments) throws StorageException, ConfigurationException, URISyntaxException {
        this.snapshotUploaderProvider = CloudDownloadUploadFactory.getUploader(arguments);
        this.arguments = arguments;
    }

    public void uploadOrFreshenFiles(final Collection<ManifestEntry> manifest) throws Exception {
        uploadOrFreshenFiles(manifest, true);
    }

    public String resolveRemotePath(Path objectKey) {
        return snapshotUploaderProvider.resolveRemotePath(objectKey);
    }

    public void uploadOrFreshenFiles(final Collection<ManifestEntry> manifest, final boolean deleteOnClose) throws Exception {
        if (manifest.isEmpty()) {
            logger.info("0 files to upload.");
            return;
        }

        final RateLimiter objectFreshenRateLimiter = RateLimiter.create(10);

        final long filesSizeSum = manifest.stream().map(e -> e.size).reduce(0L, Long::sum);

        if (arguments.duration != null) {
            long bps = filesSizeSum / arguments.duration.asSeconds().value;
            if (arguments.bandwidth != null)
                bps = Math.min(arguments.bandwidth.asBytesPerSecond().value, bps);

            bps = Math.max(new DataRate(500L, DataRate.DataRateUnit.KBPS).asBytesPerSecond().value, bps);

            arguments.bandwidth = new DataRate(bps, DataRate.DataRateUnit.BPS);
        }

        final Function<InputStream, InputStream> wrapWithRateLimitedStream = arguments.bandwidth == null ? Function.<InputStream>identity() : ((Supplier<Function<InputStream, InputStream>>) () -> {
            final RateLimiter rateLimiter = RateLimiter.create(arguments.bandwidth.asBytesPerSecond().value);
            logger.info("Upload bandwidth capped at {}.", arguments.bandwidth);

            return (s) -> new RateLimitedInputStream(s, rateLimiter);
        }).get();

        logger.info("{} files to upload. Total size {}.", manifest.size(), DataSize.bytesToHumanReadable(filesSizeSum));

        try (final SnapshotUploader fileUploader = snapshotUploaderProvider) {

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fileUploader.close();

                } catch (final Exception e) {
                    logger.error("Failed to close {}.", fileUploader.getClass().getSimpleName(), e);
                }
            }));

            // create a ThreadPoolExecutor with a fixed size queue RejectedExecutionHandler that retries queue.offer() as queue.put() (blocking)
            final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(arguments.concurrentConnections));

            final CountDownLatch completionLatch = new CountDownLatch(manifest.size() - 1); // latch to prevent the manifest from being uploaded until the very end

            final Iterable<Future<?>> uploadResults = manifest.stream().map((manifestEntry) -> {
                try {
                    return executorService.submit(() -> {
                        try (final InputStream s = new SeekableByteChannelInputStream(FileChannel.open(manifestEntry.localFile, StandardOpenOption.READ, deleteOnClose? StandardOpenOption.DELETE_ON_CLOSE: StandardOpenOption.READ))) {
                            logger.debug("Uploading file \"{}\" ({}). {} files to go.", manifestEntry.objectKey, DataSize.bytesToHumanReadable(manifestEntry.size), completionLatch.getCount());

                            if (manifestEntry.type == ManifestEntry.Type.MANIFEST_FILE)
                                completionLatch.await();

                            final RemoteObjectReference remoteObjectReference = fileUploader.objectKeyToRemoteReference(manifestEntry.objectKey);

                            try {
                                objectFreshenRateLimiter.acquire();

                                if (fileUploader.freshenRemoteObject(remoteObjectReference) == SnapshotUploader.FreshenResult.FRESHENED)
                                    return null; // file is fresh, skip upload

                            } catch (final InterruptedException e) {
                                throw e;

                            } catch (final Exception e) {
                                logger.warn("Failed to freshen file \"{}\".", manifestEntry.objectKey, e);
                            }


                            final InputStream rateLimitedStream = wrapWithRateLimitedStream.apply(s);
                            fileUploader.uploadSnapshotFile(manifestEntry.size, rateLimitedStream, remoteObjectReference);

//                            logger.info("Successfully uploaded file \"{}\". ({} / second)", manifestEntry.objectKey, DataSize.bytesToHumanReadable((long) (uploadThroughputMeter.getOneMinuteRate() / 60.0)));

                            return null;

                        } catch (final Throwable t) {
                            logger.error("Failed to upload file \"{}\".", manifestEntry.objectKey, t);

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

            // wait for uploads to finish
            executorService.shutdown();
            while (true) {
                if (executorService.awaitTermination(1, TimeUnit.MINUTES))
                    break;
            }

            // rethrow any exception caused by an upload task so we exit with failure
            for (final Future<?> result : uploadResults) {
                result.get();
            }
        }
    }
}
