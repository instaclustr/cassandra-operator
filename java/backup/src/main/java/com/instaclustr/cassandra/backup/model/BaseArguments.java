package com.instaclustr.cassandra.backup.model;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.PathOptionHandler;

public abstract class BaseArguments {
    final String appName;
    final PrintStream stream;
    CmdLineParser parser;
    public String account;
    public String secret;

    BaseArguments(final String appName, final PrintStream stream) {
        this.appName = appName;
        this.stream = stream;
    }

    public BaseArguments() {
        this.appName = "";
        this.stream = null;
    }

    public void parseArguments(String[] args) {
        try {
            this.parser = new CmdLineParser(this, ParserProperties.defaults().withUsageWidth(120).withOptionSorter(null));
            this.parser.parseArgument(args);

        } catch (final CmdLineException e) {
            printError(e.getLocalizedMessage());
        }

        if (this.showHelp) {
            printUsage();
            printHelp();
            System.exit(1);
        }
    }

    void printError(String message) {
        stream.format("ERROR %s: ", appName);
        stream.println(message);
        stream.println();
        printUsage();
        stream.format("For detailed help run `%s --help`.%n", appName);
        System.exit(1);
    }

    private void printUsage() {
        stream.print("Usage: ");
        stream.print(appName);
        parser.printSingleLineUsage(stream);
        stream.println();
        parser.printUsage(stream);
        stream.println();
    }

    abstract void printHelp();

    @Option(name = "--concurrent-connections", usage = "Number of files (or file parts) to upload or download concurrently. Higher values will increase throughput. Default is 10.", metaVar = "count")
    public Integer concurrentConnections = 10;

    @Option(name = "--wait", usage = "Wait to acquire the global transfer lock (which prevents more than one com.instaclustr.backup or restore from running).")
    public boolean waitForLock;

    @Option(name = "--help", usage = "Show this message.", help = true)
    public boolean showHelp;


    @Option(name = "--bs", aliases = {"--blob-storage"}, usage = "Blob storage provider (AWS, AZURE, GCP, FILE)", metaVar = "FILE")
    public StorageProvider storageProvider;

    @Option(name = "-c", aliases = {"--cluster"}, metaVar = "cluster ID", usage = "Parent cluster of node to restore from.", required = true)
    public String clusterId;

    public void setParser(CmdLineParser parser) {
        this.parser = parser;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setConcurrentConnections(Integer concurrentConnections) {
        this.concurrentConnections = concurrentConnections;
    }

    public void setWaitForLock(boolean waitForLock) {
        this.waitForLock = waitForLock;
    }

    public void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    public void setStorageProvider(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public void setCassandraDirectory(@Nullable Path cassandraDirectory) {
        this.cassandraDirectory = cassandraDirectory;
    }

    public void setFileBackupDirectory(@Nullable Path fileBackupDirectory) {
        this.fileBackupDirectory = fileBackupDirectory;
    }

    public void setCassandraConfigDirectory(@Nullable Path cassandraConfigDirectory) {
        this.cassandraConfigDirectory = cassandraConfigDirectory;
    }

    public void setSharedContainerPath(@Nullable Path sharedContainerPath) {
        this.sharedContainerPath = sharedContainerPath;
    }

    //TODO: Allow user to override commitlog directory (some environments may allow different disks which better suit commitlog performance
    @Option(name = "--dd", aliases = {"--data-directory"}, usage = "Base directory that contains the Cassandra data, cache and commitlog directories", metaVar = "/cassandra", handler = PathOptionHandler.class)
    @Nullable
    public Path cassandraDirectory = Paths.get("/var/lib/cassandra/");


    //TODO: Allow user to override commitlog directory (some environments may allow different disks which better suit commitlog performance
    @Option(name = "--fl", aliases = {"--filebackup-location"}, usage = "Base directory destination for filesystem based backups", metaVar = "/backups", handler = PathOptionHandler.class)
    @Nullable
    public Path fileBackupDirectory;

    //TODO: Allow user to override commitlog directory (some environments may allow different disks which better suit commitlog performance
    @Option(name = "--cd", aliases = {"--config-directory"}, usage = "Base directory that contains the Cassandra data, cache and commitlog directories", metaVar = "/cassandra", handler = PathOptionHandler.class)
    @Nullable
    public Path cassandraConfigDirectory = Paths.get("/etc/cassandra/");


    @Option(name = "-p", aliases = {"--shared-path"}, usage = "Shared Container path for pod", metaVar = "/", handler = PathOptionHandler.class)
    @Nullable
    public Path sharedContainerPath = Paths.get("/");

}
