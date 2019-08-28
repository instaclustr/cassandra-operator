package com.instaclustr.cassandra.backup;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.instaclustr.cassandra.backup.guice.BackuperFactory;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;
import com.instaclustr.cassandra.backup.impl.ManifestEntry;
import com.instaclustr.cassandra.backup.impl.SSTableUtils;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.cassandra.backup.impl.backup.BackupCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperation;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;
import com.instaclustr.cassandra.backup.impl.restore.RestoreCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperation;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;
import com.instaclustr.cassandra.backup.local.LocalFileBackuper;
import com.instaclustr.cassandra.backup.local.LocalFileRestorer;
import com.instaclustr.threading.Executors.FixedTasksExecutor;
import jmx.org.apache.cassandra.CassandraVersion;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class BackupRestoreTest {

    public static final CassandraVersion THREE = CassandraVersion.parse("3.0.0");

    private final String sha1Hash = "3a1bd6900872256303b1ed036881cd35f5b670ce";
    private String testSnapshotName = "testSnapshot";
    // Adler32 computed by python
    // zlib.adler32("dnvbjaksdbhr7239iofhusdkjfhgkauyg83uhdjshkusdhoryhjzdgfk8ei") & 0xffffffff -> 2973505342
    private static final String nodeId = "DUMMY_NODE_ID";
    private static final String clusterId = "DUMMY_CLUSTER_ID";
    private static final String backupBucket = Optional.ofNullable(System.getenv("TEST_BUCKET")).orElse("fooo");
    private final Long independentChecksum = 2973505342L;
    private List<String> tokens = ImmutableList.of("1", "2", "3", "4", "5");
    private String confDir = "config";

    private final List<TestFileConfig> versionsToTest = ImmutableList.of(
            new TestFileConfig(sha1Hash, THREE)
    );


    private static final Map<String, Path> tempDirs = new LinkedHashMap<>();

    @BeforeClass(alwaysRun = true)
    public void setup() throws IOException, URISyntaxException {
        for (TestFileConfig testFileConfig : versionsToTest) {
            Path containerTempRoot = Files.createTempDirectory(testFileConfig.cassandraVersion.toString());
            Path containerBackupRoot = Files.createTempDirectory(testFileConfig.cassandraVersion.toString());
            BackupRestoreTestUtils.createTempDirectories(containerTempRoot, BackupRestoreTestUtils.cleanableDirs);
            BackupRestoreTestUtils.createTempDirectories(containerTempRoot, BackupRestoreTestUtils.uncleanableDirs);
            tempDirs.put(testFileConfig.cassandraVersion.toString(), containerTempRoot);
            tempDirs.put(testFileConfig.cassandraVersion.toString() + "-backup-location", containerBackupRoot);

        }
        BackupRestoreTestUtils.resetDirectories(versionsToTest, tempDirs, testSnapshotName);
    }


    private List<Path> resolveSSTableComponentPaths(final String keyspace, final String table, final Path cassandraRoot, final int sequence, final TestFileConfig testFileConfig) {
        return BackupRestoreTestUtils.SSTABLE_FILES.stream()
                .map(name -> cassandraRoot.resolve("data")
                        .resolve(keyspace)
                        .resolve(table)
                        .resolve(String.format("%s-%s-big-%s", testFileConfig.getSstablePrefix(keyspace, table), sequence, name)))
                .collect(Collectors.toList());
    }


    private void testBackupAndRestore(final BackupOperationRequest backupRequest,
                                      final RestoreOperationRequest restoreRequest,
                                      final TestFileConfig testFileConfig) throws Exception {
        final Path sharedContainerRoot = backupRequest.sharedContainerPath;
        final File manifestFile = new File(sharedContainerRoot.resolve("manifests/" + testSnapshotName).toString());

        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));

        calendar.set(2017, Calendar.MAY, 2, 2, 6, 0);
//        restoreRequest.timestampStart = calendar.getTimeInMillis();
//        restoreRequest.timestampEnd = System.currentTimeMillis();

        new BackupOperation(null,
                            new HashMap<String, BackuperFactory>() {{
                                put("file", new BackuperFactory() {
                                    @Override
                                    public Backuper createBackuper(final BackupOperationRequest backupOperationRequest) {
                                        return new LocalFileBackuper(new FixedTasksExecutor(), backupRequest);
                                    }

                                    @Override
                                    public Backuper createCommitLogBackuper(final BackupCommitLogsOperationRequest backupCommitLogsOperationRequest) {
                                        return null;
                                    }
                                });
                            }},
                            backupRequest).run();

        BackupRestoreTestUtils.clearDirs(backupRequest.sharedContainerPath, BackupRestoreTestUtils.cleanableDirs);
        BackupRestoreTestUtils.createConfigFiles(sharedContainerRoot.resolve(confDir));


        //Make sure we deleted the files
        restoreRequest.keyspaceTables.entries().forEach(x -> {
            resolveSSTableComponentPaths(x.getKey(), x.getValue(), sharedContainerRoot, 1, testFileConfig).stream()
                    .map(Path::toFile)
                    .map(File::exists)
                    .forEach(Assert::assertFalse);
        });

        new RestoreOperation(new HashMap<String, RestorerFactory>() {{
            put("file", new RestorerFactory() {
                @Override
                public Restorer createRestorer(final RestoreOperationRequest restoreOperationRequest) {
                    return new LocalFileRestorer(new FixedTasksExecutor(), restoreRequest);
                }

                @Override
                public Restorer createCommitLogRestorer(final RestoreCommitLogsOperationRequest restoreCommitLogsOperationRequest) {
                    return null;
                }
            });
        }}, restoreRequest).run();

        // Confirm manifest downloaded
        assertTrue(manifestFile.exists());

        restoreRequest.keyspaceTables.entries().forEach(x -> {
            Stream.of(1, 2, 3).forEach(sequence ->
                                               resolveSSTableComponentPaths(x.getKey(), x.getValue(), sharedContainerRoot, sequence, testFileConfig).stream()
                                                       .map(Path::toFile)
                                                       .map(File::exists)
                                                       .forEach(Assert::assertTrue));
        });


        // Confirm cassandra.yaml present and includes tokens
        final Path cassandraYaml = sharedContainerRoot.resolve(confDir).resolve("cassandra.yaml");
        assertTrue(cassandraYaml.toFile().exists());
        String cassandraYamlText = new String(Files.readAllBytes(cassandraYaml));
//            Assert.assertTrue(cassandraYamlText.contains("initial_token: ")); //this is not really testing that we have configured tokens properly
//            Assert.assertTrue(cassandraYamlText.contains("auto_bootstrap: false"));
//            Assert.assertFalse(cassandraYamlText.contains("auto_bootstrap: true"));

    }


    @Test(description = "Full backup and restore to an existing cluster", groups = {"basic"})
    public void basicRestore() throws Exception {
        basicProviderBackupRestore(backupBucket);
    }

//    @Test(description = "Full backup and restore to an existing cluster", groups = {"gcp"})
//    public void basicGCPRestore() throws Exception {
//        //TODO: make it easier to test multiple different buckets (from diff providers in one test run)
//        basicProviderBackupRestore(StorageProviders.GCP_BLOB, bucket);
//    }
//
//    @Test(description = "Full backup and restore to an existing cluster", groups = {"aws"})
//    public void basicAWSRestore() throws Exception {
//        basicProviderBackupRestore(StorageProviders.AWS_S3, bucket);
//    }
//
//    @Test(description = "Full backup and restore to an existing cluster", groups = {"azure"})
//    public void basicAzureRestore() throws Exception {
//        basicProviderBackupRestore(StorageProviders.AZURE_BLOB, bucket);
//    }


    public void basicProviderBackupRestore(final String bucket) throws Exception {
        final String keyspace = "keyspace1";
        final String table = "table1";
        for (TestFileConfig testFileConfig : versionsToTest) {
            final Path sharedContainerRoot = tempDirs.get(testFileConfig.cassandraVersion.toString());
            final Path backupPath = tempDirs.get(testFileConfig.cassandraVersion.toString() + "-backup-location");

            final StorageLocation storageLocation = new StorageLocation(String.format("file://%s/%s/%s/%s",
                                                                                      backupPath.toString(),
                                                                                      bucket,
                                                                                      clusterId,
                                                                                      nodeId));

            final BackupOperationRequest backupRequest = new BackupOperationRequest(
                    storageLocation,
                    null,
                    null,
                    10,
                    true,
                    sharedContainerRoot,
                    sharedContainerRoot,
                    ImmutableList.of(),
                    testSnapshotName,
                    true,
                    null
            );

            final RestoreOperationRequest restoreRequest = new RestoreOperationRequest(
                    storageLocation,
                    10,
                    true,
                    sharedContainerRoot,
                    sharedContainerRoot,
                    sharedContainerRoot,
                    true,
                    testSnapshotName,
                    ImmutableMultimap.of()
            );

            testBackupAndRestore(backupRequest, restoreRequest, testFileConfig);
        }
    }

    @Test(description = "Check that we are checksumming properly")
    public void testCalculateDigest() throws Exception {
        for (TestFileConfig testFileConfig : versionsToTest) {
            final String keyspace = "keyspace1";
            final String table1 = "table1";
            final Path table1Path = tempDirs.get(testFileConfig.cassandraVersion.toString()).resolve("data/" + keyspace + "/" + table1);
            final Path path = table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)));
            final String checksum = SSTableUtils.calculateChecksum(path);
            assertEquals(checksum, String.valueOf(independentChecksum));
        }
    }

    @BeforeTest
    private void hardResetTestDirs() throws IOException, URISyntaxException {
        cleanUp();
        setup();
    }


    @Test(description = "Test that the manifest is correctly constructed, includes expected files and generates checksum if necessary")
    public void testSSTableLister() throws Exception {
        hardResetTestDirs(); //TODO not sure why this doesn't recreate things fully given its called before each test
        for (TestFileConfig testFileConfig : versionsToTest) {
            Path backupRoot = Paths.get("/backupRoot/keyspace1");

            final String keyspace = "keyspace1";
            final String table1 = "table1";
            final Path table1Path = tempDirs.get(testFileConfig.cassandraVersion.toString()).resolve("data/" + keyspace + "/" + table1);
            Collection<ManifestEntry> manifest = SSTableUtils.ssTableManifest(table1Path, backupRoot.resolve(table1Path.getFileName())).collect(Collectors.toList());

            final String table2 = "table2";
            final Path table2Path = tempDirs.get(testFileConfig.cassandraVersion.toString()).resolve("data/" + keyspace + "/" + table2);
            manifest.addAll(SSTableUtils.ssTableManifest(table2Path, backupRoot.resolve(table2Path.getFileName())).collect(Collectors.toList()));

            Map<Path, Path> manifestMap = new HashMap<>();
            for (ManifestEntry e : manifest) {
                manifestMap.put(e.localFile, e.objectKey);
            }

            if (CassandraVersion.isTwoZero(testFileConfig.cassandraVersion)) {
                // table1 is un-compressed so should have written out a sha1 digest
                final Path localPath1 = table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)));

                assertEquals(manifestMap.get(localPath1),
                             backupRoot.resolve(String.format("%s/1-%s/%s-1-big-Data.db", table1, sha1Hash, testFileConfig.getSstablePrefix(keyspace, table1))));

                final Path localPath2 = table1Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table1)));
                final String checksum2 = SSTableUtils.calculateChecksum(localPath2);

                assertEquals(manifestMap.get(localPath2),
                             backupRoot.resolve(String.format("%s/3-%s/%s-3-big-Index.db", table1, checksum2, testFileConfig.getSstablePrefix(keyspace, table1))));

                final Path localPath3 = table2Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table2)));
                final String checksum3 = SSTableUtils.calculateChecksum(localPath3);

                assertEquals(manifestMap.get(localPath3),
                             backupRoot.resolve(String.format("%s/1-%s/%s-1-big-Data.db", table2, checksum3, testFileConfig.getSstablePrefix(keyspace, table2))));

                assertNull(manifestMap.get(table2Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table2)))));
            } else {
                assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)))),
                             backupRoot.resolve(String.format("%s/1-1000000000/%s-1-big-Data.db", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                // Cassandra doesn't create CRC32 file for 2.0.x
                assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-2-big-Digest.crc32", testFileConfig.getSstablePrefix(keyspace, table1)))),
                             backupRoot.resolve(String.format("%s/2-1000000000/%s-2-big-Digest.crc32", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table1)))),
                             backupRoot.resolve(String.format("%s/3-1000000000/%s-3-big-Index.db", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                assertEquals(manifestMap.get(table2Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table2)))),
                             backupRoot.resolve(String.format("%s/1-1000000000/%s-1-big-Data.db", table2, testFileConfig.getSstablePrefix(keyspace, table2))));

                assertEquals(manifestMap.get(table2Path.resolve(String.format("%s-2-big-Digest.crc32", testFileConfig.getSstablePrefix(keyspace, table2)))),
                             backupRoot.resolve(String.format("%s/2-1000000000/%s-2-big-Digest.crc32", table2, testFileConfig.getSstablePrefix(keyspace, table2))));

                assertNull(manifestMap.get(table2Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table2)))));
            }

            assertNull(manifestMap.get(table1Path.resolve("manifest.json")));
            assertNull(manifestMap.get(table1Path.resolve("backups")));
            assertNull(manifestMap.get(table1Path.resolve("snapshots")));
        }
    }


    @AfterClass(alwaysRun = true)
    public void cleanUp() throws IOException {
        BackupRestoreTestUtils.deleteTempDirectories(tempDirs);
    }
}