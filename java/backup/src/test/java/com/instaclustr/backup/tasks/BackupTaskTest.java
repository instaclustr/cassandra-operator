package com.instaclustr.backup.tasks;

import com.google.common.collect.*;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.StorageProvider;
import com.instaclustr.backup.jmx.CassandraVersion;
import com.instaclustr.backup.service.TestHelperService;
import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.task.ManifestEntry;
import com.instaclustr.backup.task.RestoreTask;
import com.instaclustr.backup.util.Directories;
import com.instaclustr.backup.util.GlobalLock;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class BackupTaskTest {
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
            new TestFileConfig(sha1Hash, CassandraVersion.THREE)
    );


    private static final Map<String, Path> tempDirs = new LinkedHashMap<>();





    @BeforeClass(alwaysRun=true)
    public void setup() throws IOException, URISyntaxException {
        for (TestFileConfig testFileConfig : versionsToTest) {
            Path containerTempRoot = Files.createTempDirectory(testFileConfig.cassandraVersion.name());
            TestHelperService.createTempDirectories(containerTempRoot, TestHelperService.cleanableDirs);
            TestHelperService.createTempDirectories(containerTempRoot, TestHelperService.uncleanableDirs);
            tempDirs.put(testFileConfig.cassandraVersion.name(), containerTempRoot);

        }
        TestHelperService.resetDirectories(versionsToTest, tempDirs, testSnapshotName);
    }







    private List<Path> resolveSSTableComponentPaths(final String keyspace, final String table, final Path cassandraRoot, final int sequence, final TestFileConfig testFileConfig) {
        return TestHelperService.SSTABLE_FILES.stream()
                .map(name -> cassandraRoot.resolve(Directories.CASSANDRA_DATA)
                        .resolve(keyspace)
                        .resolve(table)
                        .resolve(String.format("%s-%s-big-%s", testFileConfig.getSstablePrefix(keyspace, table) ,sequence, name)))
                .collect(Collectors.toList());
    }


    private void testBackupAndRestore(final BackupArguments backupArguments, final RestoreArguments restoreArguments, final TestFileConfig testFileConfig) throws Exception {
            final Path sharedContainerRoot = backupArguments.sharedContainerPath;
            final File manifestFile = new File(sharedContainerRoot.resolve("manifests/" + testSnapshotName).toString());

            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeZone(TimeZone.getTimeZone("GMT"));

            calendar.set(2017, Calendar.MAY, 2, 2, 6, 0);
            restoreArguments.timestampStart = calendar.getTimeInMillis();
            restoreArguments.timestampEnd = System.currentTimeMillis();


            new BackupTask(backupArguments, new GlobalLock(sharedContainerRoot.toString())).call();

            TestHelperService.clearDirs(backupArguments.sharedContainerPath, TestHelperService.cleanableDirs);
            TestHelperService.createConfigFiles(sharedContainerRoot.resolve(confDir));


            //Make sure we deleted the files
            restoreArguments.keyspaceTables.entries().forEach(x -> {
                resolveSSTableComponentPaths(x.getKey(), x.getValue(), sharedContainerRoot, 1, testFileConfig).stream()
                        .map(Path::toFile)
                        .map(File::exists)
                        .forEach(Assert::assertFalse);
            });



            new RestoreTask(new GlobalLock(sharedContainerRoot.toString()), restoreArguments).call();

            // Confirm manifest downloaded
            Assert.assertTrue(manifestFile.exists());


            restoreArguments.keyspaceTables.entries().forEach(x -> {
                Stream.of(1,2,3).forEach(sequence ->
                        resolveSSTableComponentPaths(x.getKey(), x.getValue(), sharedContainerRoot, sequence, testFileConfig).stream()
                                .map(Path::toFile)
                                .map(File::exists)
                                .forEach(Assert::assertTrue));
            });


            // Confirm cassandra.yaml present and includes tokens
            final Path cassandraYaml = sharedContainerRoot.resolve(confDir).resolve("cassandra.yaml");
            Assert.assertTrue(cassandraYaml.toFile().exists());
            String cassandraYamlText = new String(Files.readAllBytes(cassandraYaml));
//            Assert.assertTrue(cassandraYamlText.contains("initial_token: ")); //this is not really testing that we have configured tokens properly
//            Assert.assertTrue(cassandraYamlText.contains("auto_bootstrap: false"));
//            Assert.assertFalse(cassandraYamlText.contains("auto_bootstrap: true"));

    }


    @Test(description = "Full backup and restore to an existing cluster", groups = {"basic"})
    public void basicRestore() throws Exception {
        basicProviderBackupRestore(StorageProvider.FILE, backupBucket);
    }

//    @Test(description = "Full backup and restore to an existing cluster", groups = {"gcp"})
//    public void basicGCPRestore() throws Exception {
//        //TODO: make it easier to test multiple different buckets (from diff providers in one test run)
//        basicProviderBackupRestore(StorageProvider.GCP_BLOB, backupBucket);
//    }
//
//    @Test(description = "Full backup and restore to an existing cluster", groups = {"aws"})
//    public void basicAWSRestore() throws Exception {
//        basicProviderBackupRestore(StorageProvider.AWS_S3, backupBucket);
//    }
//
//    @Test(description = "Full backup and restore to an existing cluster", groups = {"azure"})
//    public void basicAzureRestore() throws Exception {
//        basicProviderBackupRestore(StorageProvider.AZURE_BLOB, backupBucket);
//    }


    public void basicProviderBackupRestore(final StorageProvider provider, final String backupBucket) throws Exception {
        final String keyspace = "keyspace1";
        final String table = "table1";
        for (TestFileConfig testFileConfig : versionsToTest) {
            final Path sharedContainerRoot = tempDirs.get(testFileConfig.cassandraVersion.name());

            final List<String> rawBaseArguments = ImmutableList.of(
                    "--bs", provider.toString(),
                    "--dd", sharedContainerRoot.toString(),
                    "--cd", sharedContainerRoot.resolve(confDir).toString(),
                    "--fl", sharedContainerRoot.resolve("backups").toString(),
                    "-c", clusterId,
                    "-p", sharedContainerRoot.toString()
            );

            final List<String> rawCommonBackupArguments = ImmutableList.of(
//                    "-j", "JMXxxxxx",
                    "--bucket", backupBucket,
                    "--id", nodeId
            );
            final List<String> rawBackupArguments = ImmutableList.of(
                    "-t", testSnapshotName,
                    "--offline", "true"
            );

            final List<String> rawRestoreArguments = ImmutableList.of(
                    "-bi", nodeId,
                    "-bb", backupBucket,
                    "-s", testSnapshotName,
                    "-kt", keyspace + "." + table
            );

            final BackupArguments backupArguments = new BackupArguments("cassandra-test", System.err);
            backupArguments.parseArguments(Stream.of(rawBaseArguments, rawCommonBackupArguments, rawBackupArguments)
                    .flatMap(List::stream)
                    .toArray(String[]::new));


            final RestoreArguments restoreArguments = new RestoreArguments("cassandra-restore", System.err);
            restoreArguments.parseArguments(Stream.of(rawBaseArguments, rawRestoreArguments)
                    .flatMap(List::stream)
                    .toArray(String[]::new));


            testBackupAndRestore(backupArguments, restoreArguments, testFileConfig);

        }
    }


    @Test(description = "Check that we are checksumming properly")
    public void testCalculateDigest() throws Exception {
        for (TestFileConfig testFileConfig : versionsToTest) {
            final String keyspace = "keyspace1";
            final String table1 = "table1";
            final Path table1Path = tempDirs.get(testFileConfig.cassandraVersion.name()).resolve("data/" + keyspace + "/" + table1);
            final Path path = table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)));
            final String checksum = BackupTask.calculateChecksum(path);
            Assert.assertEquals(checksum, String.valueOf(independentChecksum));
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
            final Path table1Path = tempDirs.get(testFileConfig.cassandraVersion.name()).resolve("data/" + keyspace + "/" + table1);
            Collection<ManifestEntry> manifest = BackupTask.ssTableManifest(table1Path, backupRoot.resolve(table1Path.getFileName()));

            final String table2 = "table2";
            final Path table2Path = tempDirs.get(testFileConfig.cassandraVersion.name()).resolve("data/" + keyspace + "/" + table2);
            manifest.addAll(BackupTask.ssTableManifest(table2Path, backupRoot.resolve(table2Path.getFileName())));

            Map<Path, Path> manifestMap = new HashMap<>();
            for (ManifestEntry e : manifest) {
                manifestMap.put(e.localFile, e.objectKey);
            }

            if (testFileConfig.cassandraVersion == CassandraVersion.TWO_ZERO) {
                // table1 is un-compressed so should have written out a sha1 digest
                final Path localPath1 = table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)));

                Assert.assertEquals(manifestMap.get(localPath1),
                        backupRoot.resolve(String.format("%s/1-%s/%s-1-big-Data.db", table1, sha1Hash, testFileConfig.getSstablePrefix(keyspace, table1))));

                final Path localPath2 = table1Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table1)));
                final String checksum2 = BackupTask.calculateChecksum(localPath2);

                Assert.assertEquals(manifestMap.get(localPath2),
                        backupRoot.resolve(String.format("%s/3-%s/%s-3-big-Index.db", table1, checksum2, testFileConfig.getSstablePrefix(keyspace, table1))));

                final Path localPath3 = table2Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table2)));
                final String checksum3 = BackupTask.calculateChecksum(localPath3);

                Assert.assertEquals(manifestMap.get(localPath3),
                        backupRoot.resolve(String.format("%s/1-%s/%s-1-big-Data.db", table2, checksum3, testFileConfig.getSstablePrefix(keyspace, table2))));

                Assert.assertNull(manifestMap.get(table2Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table2)))));
            } else {
                Assert.assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table1)))),
                        backupRoot.resolve(String.format("%s/1-1000000000/%s-1-big-Data.db", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                // Cassandra doesn't create CRC32 file for 2.0.x
                Assert.assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-2-big-Digest.crc32", testFileConfig.getSstablePrefix(keyspace, table1)))),
                        backupRoot.resolve(String.format("%s/2-1000000000/%s-2-big-Digest.crc32", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                Assert.assertEquals(manifestMap.get(table1Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table1)))),
                        backupRoot.resolve(String.format("%s/3-1000000000/%s-3-big-Index.db", table1, testFileConfig.getSstablePrefix(keyspace, table1))));

                Assert.assertEquals(manifestMap.get(table2Path.resolve(String.format("%s-1-big-Data.db", testFileConfig.getSstablePrefix(keyspace, table2)))),
                        backupRoot.resolve(String.format("%s/1-1000000000/%s-1-big-Data.db", table2, testFileConfig.getSstablePrefix(keyspace, table2))));

                Assert.assertEquals(manifestMap.get(table2Path.resolve(String.format("%s-2-big-Digest.crc32", testFileConfig.getSstablePrefix(keyspace, table2)))),
                        backupRoot.resolve(String.format("%s/2-1000000000/%s-2-big-Digest.crc32", table2, testFileConfig.getSstablePrefix(keyspace, table2))));

                Assert.assertNull(manifestMap.get(table2Path.resolve(String.format("%s-3-big-Index.db", testFileConfig.getSstablePrefix(keyspace, table2)))));
            }

            Assert.assertNull(manifestMap.get(table1Path.resolve("manifest.json")));
            Assert.assertNull(manifestMap.get(table1Path.resolve("backups")));
            Assert.assertNull(manifestMap.get(table1Path.resolve("snapshots")));
        }
    }




    @AfterClass(alwaysRun = true)
    public void cleanUp () throws IOException {
        TestHelperService.deleteTempDirectories(tempDirs);
    }
}
