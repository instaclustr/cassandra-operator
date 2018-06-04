package com.instaclustr.backup;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BackupArguments extends CommonBackupArguments {
    public BackupArguments(final String appName, final PrintStream stream) {
        super(appName, stream);
    }

    @Override
    public void parseArguments(String[] args) {
        super.parseArguments(args);

        if (columnFamily != null && keyspaces.size() != 1) {
            super.printError(String.format("--column-family requires exactly one keyspace name, %d given", keyspaces.size()));
        }

        // generate a snapshot tag if not specified
        if (snapshotTag == null) {
            snapshotTag = String.format("autosnap-%d", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        }

    }
    
    @Override
    void commonBackupPrintHelp() {
        stream.println("Take a snapshot of this nodes Cassandra data and upload it to remote storage.\n" +
                "\n" +
                "Defaults to a snapshot of all keyspaces and their column families, but may be restricted to specific keyspaces or a single column-family.\n");
    }

    @Option(name = "-t", aliases = {"--tag"}, usage = "Snapshot tag name. Default is equiv. to 'autosnap-`date +%s`'", metaVar = "snapshot-tag")
    public String snapshotTag;

    @Option(name="--cf", aliases = "--column-family", metaVar = "name", usage = "The column family to snapshot/upload. Requires a keyspace to be specified.")
    @Nullable
    public String columnFamily;

    @Option(name = "--drain", usage = "Optionally drain Cassandra following snapshot.")
    public boolean drain;

    @Argument(index = 0, metaVar = "keyspace")
    public List<String> keyspaces = new ArrayList<>();
}
