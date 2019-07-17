package com.instaclustr.cassandra.backup.impl.restore;

import static com.instaclustr.cassandra.backup.impl.restore.Restorer.CompareFilesResult.DOWNLOAD_REQUIRED;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.util.concurrent.Futures;
import com.instaclustr.threading.Executors.ExecutorServiceSupplier;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.cassandra.backup.impl.SSTableUtils;
import com.instaclustr.cassandra.backup.impl.StorageInteractor;
import com.instaclustr.io.FileUtils;
import com.instaclustr.io.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Restorer extends StorageInteractor {
    private static final Logger logger = LoggerFactory.getLogger(Restorer.class);

    private final static String CASSANDRA_DATA = "data";
    private final static String CASSANDRA_COMMIT_LOGS = "commitlog";
    private final static String CASSANDRA_COMMIT_RESTORE = "commitlog_restore";

    protected final RestoreOperationRequest request;
    private final ExecutorServiceSupplier executorServiceSupplier;

    // maybe not necessary?
    private final Path commitLogRestoreDirectory;
    private final Path fullCommitLogRestoreDirectory;

    public Restorer(final RestoreOperationRequest request,
                    final ExecutorServiceSupplier executorServiceSupplier) {
        super(request.storageLocation);
        this.request = request;
        this.executorServiceSupplier = executorServiceSupplier;

        this.commitLogRestoreDirectory = request.cassandraDirectory.resolve(CASSANDRA_COMMIT_RESTORE); //TODO: hardcoded path, make this an argument when we get to supporting CL
        this.fullCommitLogRestoreDirectory = request.sharedContainerPath.resolve(this.commitLogRestoreDirectory.subpath(0, this.commitLogRestoreDirectory.getNameCount()));
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

    public abstract void downloadFile(final Path localPath, final RemoteObjectReference object) throws Exception;

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

    public void restore() throws Exception {
        // 1. TODO: Check cassandra still running. Halt if running? Make this restore task a pre-start container for the pod to avoid this check

        new GlobalLock(request.sharedContainerPath).waitForLock(request.waitForLock);

        //final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createRestorer(request);

        // 2. Determine if just restoring a subset of tables
        final boolean isTableSubsetOnly = request.keyspaceTables.size() > 0;

        // 3. Download the manifest
        logger.info("Retrieving manifest for snapshot: {}", request.snapshotTag);
        final Path sourceManifest = Paths.get("manifests/" + request.snapshotTag);
        final Path localManifest = request.sharedContainerPath.resolve(sourceManifest);

        final RemoteObjectReference manifestRemoteObjectReference = objectKeyToRemoteReference(sourceManifest);
        downloadFile(localManifest, manifestRemoteObjectReference);

        // 4. Clean out old data
        FileUtils.cleanDirectory(fullCommitLogRestoreDirectory.toFile());
        FileUtils.cleanDirectory(request.cassandraDirectory.resolve("hints").toFile());
        FileUtils.cleanDirectory(request.cassandraDirectory.resolve("saved_caches").toFile());

        // 5. Build a list of all SSTables currently present, that are candidates for deleting
        final Set<Path> existingSstableList = new HashSet<>();
        final int skipBackupsAndSnapshotsFolders = 4;

        final Path cassandraSstablesDirectory = request.cassandraDirectory.resolve(CASSANDRA_DATA);

        if (cassandraSstablesDirectory.toFile().exists()) {
            try (Stream<Path> paths = Files.walk(cassandraSstablesDirectory, skipBackupsAndSnapshotsFolders)) {
                if (isTableSubsetOnly) {
                    paths.filter(Files::isRegularFile)
                            .filter(RestorePredicates.isSubsetTable(request.keyspaceTables))
                            .forEach(existingSstableList::add);
                } else {
                    paths.filter(Files::isRegularFile).forEach(existingSstableList::add);
                }
            }
        }

        final boolean isRestoringToExistingCluster = existingSstableList.size() > 0;
        logger.info("Restoring to existing cluster? {}", isRestoringToExistingCluster);

        // 5. Parse the manifest
        LinkedList<ManifestEntry> downloadManifest = new LinkedList<>();

        try (BufferedReader manifestStream = Files.newBufferedReader(localManifest)) {
            List<String> filteredManifest;

            if (isRestoringToExistingCluster) {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForSubsetExistingRestore(logger, request.keyspaceTables))
                            .collect(Collectors.toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForFullExistingRestore(logger))
                            .collect(Collectors.toList());
                }
            } else {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForSubsetNewRestore(logger, request.keyspaceTables))
                            .collect(Collectors.toList());
                } else {
                    filteredManifest = manifestStream.lines()
                            .filter(RestorePredicates.getManifestFilesForFullNewRestore(logger))
                            .collect(Collectors.toList());
                }
            }

            for (final String m : filteredManifest) {
                final String[] lineArray = m.trim().split(" ");

                final Path manifestPath = Paths.get(lineArray[1]);
                final int hashPathPart = isSecondaryIndexManifest(manifestPath) ? 4 : 3;

                final Path localPath = request.cassandraDirectory.resolve(manifestPath.subpath(0, hashPathPart).resolve(manifestPath.getFileName())); //strip check hash from path

                if (isAnExistingSstable(localPath, manifestPath.getName(hashPathPart).toString())) {
                    logger.info("Keeping existing sstable " + localPath);
                    existingSstableList.remove(localPath);
                    continue; // file already present, and the hash matches so don't add to manifest to download and don't delete
                }

                logger.info("Not keeping existing sstable {}", localPath);
                downloadManifest.add(new ManifestEntry(manifestPath, localPath, ManifestEntry.Type.FILE, 0));
            }
        }

        // 6. Delete any entries left in existingSstableList
        existingSstableList.forEach(sstablePath -> {
            logger.info("Deleting existing sstable {}", sstablePath);
            if (!sstablePath.toFile().delete()) {
                logger.warn("Failed to delete {}", sstablePath);
            }
        });

        // 7. Download files in the manifest
        downloadFiles(downloadManifest);
    }

    /**
     * Decides whether or not the manifest path includes secondary index files
     *
     * @param manifestPath path to manifest
     * @return true if manifest path includes secondary index files, false otherwise
     */
    private boolean isSecondaryIndexManifest(Path manifestPath) {
        // When there's a secondary index, manifest path contains 6 elements (including '.indexName' and 'hashcode')
        // '.indexName' is filtered by subpath(3,4), to avoid the other parts of the manifest path getting misidentified with the '.'
        return manifestPath.getNameCount() == 6 && manifestPath.subpath(3, 4).toString().startsWith(".");
    }

    private boolean isAnExistingSstable(final Path localPath, final String sstable) throws IOException {
        try {
            if (localPath.toFile().exists() && SSTableUtils.sstableHash(localPath).equals(sstable)) {
                return true;
            }
        } catch (IOException e) {
            // SSTableUtils.sstableHash may throw exception if SSTable has not been probably downloaded
            logger.error(e.getMessage());
        }
        return false;
    }

}
