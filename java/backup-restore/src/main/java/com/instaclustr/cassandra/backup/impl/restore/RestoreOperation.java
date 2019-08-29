package com.instaclustr.cassandra.backup.impl.restore;

import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForFullExistingRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForFullNewRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForSubsetExistingRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.getManifestFilesForSubsetNewRestore;
import static com.instaclustr.cassandra.backup.impl.restore.RestorePredicates.isSubsetTable;
import static com.instaclustr.io.FileUtils.cleanDirectory;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.SSTableUtils;
import com.instaclustr.io.FileUtils;
import com.instaclustr.io.GlobalLock;
import com.instaclustr.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreOperation extends Operation<RestoreOperationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RestoreOperation.class);

    private final static String CASSANDRA_DATA = "data";

    private final Map<String, RestorerFactory> restorerFactoryMap;

    private final Path cassandraYaml;
    private final Path tokens;

    @Inject
    public RestoreOperation(final Map<String, RestorerFactory> restorerFactoryMap,
                            @Assisted final RestoreOperationRequest request) {
        super(request);
        this.restorerFactoryMap = restorerFactoryMap;
        cassandraYaml = request.cassandraConfigDirectory.resolve("cassandra.yaml");
        tokens = request.cassandraDirectory.resolve("tokens.yaml");
    }

    @Override
    protected void run0() throws Exception {
        try (final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createRestorer(request)) {
            restore(restorer);
            // K8S will handle copying over tokens.yaml fragment and disabling bootstrap fragment to right directory to be picked up by Cassandra
            // "standalone / vanilla" Cassandra installations has to cover this manually for now.
            // in the future, we might implement automatic configuration of cassandra.yaml for standalone installations
            //setTokens();
            //disableAutoBootstrap();
        }
    }

    private void restore(final Restorer restorer) throws Exception {

        new GlobalLock(request.sharedContainerPath).waitForLock(request.waitForLock);

        // 2. Determine if just restoring a subset of tables
        final boolean isTableSubsetOnly = request.keyspaceTables.size() > 0;

        // 3. Download the manifest
        logger.info("Retrieving manifest for snapshot: {}", request.snapshotTag);
        final Path sourceManifest = Paths.get("manifests/" + request.snapshotTag);
        final Path localManifest = request.cassandraDirectory.resolve(sourceManifest);

        restorer.downloadFile(localManifest, restorer.objectKeyToRemoteReference(sourceManifest));

        // 4. Clean out old data
        cleanDirectory(request.cassandraDirectory.resolve("hints"));
        cleanDirectory(request.cassandraDirectory.resolve("saved_caches"));

        // 5. Build a list of all SSTables currently present, that are candidates for deleting
        final Set<Path> existingSstableList = new HashSet<>();
        final int skipBackupsAndSnapshotsFolders = 4;

        final Path cassandraSstablesDirectory = request.cassandraDirectory.resolve(CASSANDRA_DATA);

        if (cassandraSstablesDirectory.toFile().exists()) {
            try (Stream<Path> paths = Files.walk(cassandraSstablesDirectory, skipBackupsAndSnapshotsFolders)) {
                if (isTableSubsetOnly) {
                    paths.filter(Files::isRegularFile)
                         .filter(isSubsetTable(request.keyspaceTables))
                         .forEach(existingSstableList::add);
                } else {
                    paths.filter(Files::isRegularFile).forEach(existingSstableList::add);
                }
            }
        }

        final boolean isRestoringToExistingCluster = existingSstableList.size() > 0;
        logger.info("Restoring to existing cluster? {}", isRestoringToExistingCluster);

        // 5. Parse the manifest
        final LinkedList<ManifestEntry> downloadManifest = new LinkedList<>();

        try (final BufferedReader manifestStream = Files.newBufferedReader(localManifest)) {
            final List<String> filteredManifest;

            if (isRestoringToExistingCluster) {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                                                     .filter(getManifestFilesForSubsetExistingRestore(request.keyspaceTables, request.restoreSystemKeyspace))
                                                     .collect(toList());
                } else {
                    filteredManifest = manifestStream.lines()
                                                     .filter(getManifestFilesForFullExistingRestore(request.restoreSystemKeyspace))
                                                     .collect(toList());
                }
            } else {
                if (isTableSubsetOnly) {
                    filteredManifest = manifestStream.lines()
                                                     .filter(getManifestFilesForSubsetNewRestore(request.keyspaceTables, request.restoreSystemKeyspace))
                                                     .collect(toList());
                } else {
                    filteredManifest = manifestStream.lines()
                                                     .filter(getManifestFilesForFullNewRestore(request.restoreSystemKeyspace))
                                                     .collect(toList());
                }
            }

            for (final String m : filteredManifest) {
                final String[] lineArray = m.trim().split(" ");

                final Path manifestPath = Paths.get(lineArray[1]);
                final int hashPathPart = isSecondaryIndexManifest(manifestPath) ? 4 : 3;

                //strip check hash from path
                final Path localPath = request.cassandraDirectory.resolve(manifestPath.subpath(0, hashPathPart).resolve(manifestPath.getFileName()));

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
        restorer.downloadFiles(downloadManifest);

        // 8. download tokens
        downloadTokens(restorer);
    }

    /**
     * Decides whether or not the manifest path includes secondary index files
     *
     * @param manifestPath path to manifest
     * @return true if manifest path includes secondary index files, false otherwise
     */
    private boolean isSecondaryIndexManifest(final Path manifestPath) {
        // When there's a secondary index, manifest path contains 6 elements (including '.indexName' and 'hashcode')
        // '.indexName' is filtered by subpath(3,4), to avoid the other parts of the manifest path getting misidentified with the '.'
        return manifestPath.getNameCount() == 6 && manifestPath.subpath(3, 4).toString().startsWith(".");
    }

    private boolean isAnExistingSstable(final Path localPath, final String sstable) {
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

    private void downloadTokens(final Restorer restorer) throws Exception {
        restorer.downloadFile(tokens, restorer.objectKeyToRemoteReference(Paths.get("tokens/" + request.snapshotTag + "-tokens.yaml")));
    }

    private void setTokens() throws IOException {
        FileUtils.appendToFile(cassandraYaml, tokens);
    }

    private void disableAutoBootstrap() throws IOException {
        FileUtils.replaceInFile(cassandraYaml, "auto_bootstrap: true", "auto_bootstrap: false");
    }
}
