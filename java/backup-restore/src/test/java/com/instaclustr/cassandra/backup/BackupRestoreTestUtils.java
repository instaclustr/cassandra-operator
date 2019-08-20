package com.instaclustr.cassandra.backup;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupRestoreTestUtils {
    private static final Logger log = LoggerFactory.getLogger(BackupRestoreTestUtils.class);

    public static final ImmutableList<String> SSTABLE_FILES = ImmutableList.of("TOC.txt",
                                                                               "CompressionInfo.db",
                                                                               "Data.db",
                                                                               "Filter.db",
                                                                               "Index.db",
                                                                               "Statistics.db",
                                                                               "Summary.db");

    public static final byte[] testData = "dnvbjaksdbhr7239iofhusdkjfhgkauyg83uhdjshkusdhoryhjzdgfk8ei".getBytes();
    public static final List<Path> cleanableDirs = Stream.of("data", "hints", "config", "commitlog").map(Paths::get).collect(toList());
    public static final List<Path> uncleanableDirs = Stream.of("backups").map(Paths::get).collect(toList());

    public static void createTempDirectories(final Path root, final List<Path> directories) throws IOException {
        for (Path directory : directories) {
            Files.createDirectories(root.resolve(directory));
        }
    }

    public static void deleteTempDirectories(Map<String, Path> tempDirectories) {
        tempDirectories.forEach((name, path) -> {
            try {
                File f = path.toFile();
                FileUtils.deleteDirectory(f);
                log.info("Deleted temp directory: " + path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static void createSSTable(final Path folder,
                                     final String keyspace,
                                     final String table,
                                     final int sequence,
                                     final TestFileConfig testFileConfig,
                                     final boolean isCompressed,
                                     final String tag) throws IOException {

        final Path ssTablePath = folder.resolve(keyspace).resolve(table);
        final Path ssTableSnapshotPath = folder.resolve(keyspace).resolve(table).resolve("snapshots").resolve(tag);

        Files.createDirectories(ssTablePath);
        Files.createDirectories(ssTableSnapshotPath);

        if (testFileConfig.createDigest(isCompressed)) {
            final Path digest = ssTablePath.resolve(String.format("%s-%s-big-Digest.crc32", testFileConfig.getSstablePrefix(keyspace, table), sequence));
            try (final Writer writer = Files.newBufferedWriter(digest)) {
                writer.write(testFileConfig.getChecksum(keyspace, table));
            }
            if (tag != null)
                Files.copy(digest, ssTableSnapshotPath.resolve(digest.getFileName()));
        }

        for (String name : SSTABLE_FILES) {
            final Path path = ssTablePath.resolve(String.format("%s-%s-big-%s", testFileConfig.getSstablePrefix(keyspace, table), sequence, name));
            Files.createFile(path);
            Files.write(path, testData);
            if (tag != null)
                Files.copy(path, ssTableSnapshotPath.resolve(path.getFileName()));
        }
    }

    public static void clearDirs(final Path containerTempRoot, final List<Path> dirsToClean) {
        dirsToClean.stream().map(containerTempRoot::resolve).forEach(entry -> {
            try {
                FileUtils.deleteDirectory(entry.toFile());
                Files.createDirectories(entry);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void createConfigFiles(final Path configPath) throws IOException {
        //cassandra-env.sh
        try (final Writer writer = Files.newBufferedWriter(configPath.resolve("cassandra-env.sh"))) {
            writer.write("#bash settings\nexport JVM_OPTS=\"-Xmx4G\"");
        }

        try (final Writer writer = Files.newBufferedWriter(configPath.resolve("cassandra.yaml"))) {
            writer.write("cluster_name: Test\nauto_bootstrap: true");
        }
    }

    public static void resetDirectories(List<TestFileConfig> versionsToTest, final Map<String, Path> tempDirs, final String testSnapshotName) throws IOException {
        for (TestFileConfig testFileConfig : versionsToTest) {
            final Path root = tempDirs.get(testFileConfig.cassandraVersion.toString());
            clearDirs(root, cleanableDirs);
            final Path data = root.resolve("data");
            final Path config = root.resolve("config");
            final String keyspace = "keyspace1";
            final String table1 = "table1";
            createSSTable(data, keyspace, table1, 1, testFileConfig, false, testSnapshotName);
            createSSTable(data, keyspace, table1, 2, testFileConfig, true, testSnapshotName);
            createSSTable(data, keyspace, table1, 3, testFileConfig, true, testSnapshotName);

            final String table2 = "table2";
            createSSTable(data, keyspace, table2, 1, testFileConfig, true, testSnapshotName);
            createSSTable(data, keyspace, table2, 2, testFileConfig, true, testSnapshotName);

            createConfigFiles(config);
        }
    }
}