package com.instaclustr.backup;

import org.kohsuke.args4j.Option;

import javax.annotation.Nullable;
import java.io.PrintStream;

public class RestoreArguments extends BaseArguments {

    public RestoreArguments(final String appName, final PrintStream stream) {
        super(appName, stream);
    }

    @Override
    void printHelp() {
        stream.println("Restore the Cassandra data on this node to a specified point-in-time.");
    }

    @Option(name = "-bi", aliases = {"--com.instaclustr.backup-id"}, metaVar = "com.instaclustr.backup ID", usage = "Backup ID to restore from. Normally just the source nodeID, but if the node has replaced another node might be previous nodeID.", required = true)
    public String sourceBackupID;

    @Option(name = "-cdc", aliases = {"--cluster-data-centre"}, metaVar = "cluster data centre ID", usage = "Parent cluster data centre of node to restore from.", required = true)
    public String clusterDataCentreId;

    @Option(name = "-c", aliases = {"--cluster"}, metaVar = "cluster ID", usage = "Parent cluster of node to restore from.", required = true)
    public String clusterId;

    @Option(name = "-bb", aliases = {"--com.instaclustr.backup-bucket"}, metaVar = "bucket name", usage = "Bucket hosting the snapshot files to restore.", required = true)
    public String backupBucket;

    @Option(name = "-s", aliases = {"--snapshot-tag"}, metaVar = "tag name", usage = "Snapshot to download and restore.", required = true)
    public String snapshotTag;

    @Option(name="-kt", aliases = "--keyspace-tables", metaVar = "name", usage = "Comma separated list of tables to restore. Must include keyspace name in the format <keyspace.table>")
    @Nullable
    public String keyspaceTables;

    @Option(name = "-ts", aliases = {"--timestamp-start"}, metaVar = "Milliseconds since epoch", usage = "When the base snapshot was taken. Only relevant if archived commitlogs are available.", required = true)
    public long timestampStart;

    @Option(name = "-te", aliases = {"--timestamp-end"}, metaVar = "Milliseconds since epoch", usage = "Point-in-time to restore up to. Only relevant if archived commitlogs are available.", required = true)
    public long timestampEnd;

    @Option(name = "-rs", aliases = {"--restore-system-keyspace"}, usage = "Restore system keyspace. Use this to prevent bootstrapping, when restoring on only a single node.")
    public boolean restoreSystemKeyspace = false;

    @Option(name="-ast", aliases = "--azure-sas-token", metaVar = "token", usage = "Azure SAS token granting access to the Storage Account container to download from.")
    @Nullable
    public String azureSasToken;
}
