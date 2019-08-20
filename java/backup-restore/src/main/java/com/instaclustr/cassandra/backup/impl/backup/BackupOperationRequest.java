package com.instaclustr.cassandra.backup.impl.backup;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.measure.DataRate;
import com.instaclustr.measure.Time;
import picocli.CommandLine;

@ValidBackupOperationRequest
public class BackupOperationRequest extends BaseBackupOperationRequest {

    @CommandLine.Option(
            names = {"-s", "--st", "--snapshot-tag"},
            description = "Snapshot tag name. Default is equiv. to 'autosnap-`date +s`'"
    )
    public String snapshotTag = format("autosnap-%d", MILLISECONDS.toSeconds(currentTimeMillis()));

    @CommandLine.Option(
            names = "--offline",
            description = "Cassandra is not running (won't use JMX to snapshot, no token lists uploaded)"
    )
    public boolean offlineSnapshot;

    @CommandLine.Option(
            names = {"--cf", "--column-family"},
            description = "The column family to snapshot/upload. Requires a keyspace to be specified."
    )
    public String columnFamily;

    @CommandLine.Parameters
    public List<String> keyspaces;

    public BackupOperationRequest() {
        super();
    }

    @JsonCreator
    public BackupOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                  @JsonProperty("duration") final Time duration,
                                  @JsonProperty("bandwidth") final DataRate bandwidth,
                                  @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                  @JsonProperty("waitForLock") final boolean waitForLock,
                                  @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                  @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                  @JsonProperty("keyspaces") final List<String> keyspaces,
                                  @JsonProperty("snapshotTag") final String snapshotTag,
                                  @JsonProperty("offlineSnapshot") final boolean offlineSnapshot,
                                  @JsonProperty("columnFamily") final String columnFamily) {
        super(storageLocation, duration, bandwidth, concurrentConnections, waitForLock, sharedContainerPath, cassandraDirectory);
        this.keyspaces = keyspaces == null ? ImmutableList.of() : keyspaces;
        this.snapshotTag = snapshotTag == null ? format("autosnap-%d", MILLISECONDS.toSeconds(currentTimeMillis())) : snapshotTag;
        this.offlineSnapshot = offlineSnapshot;
        this.columnFamily = columnFamily;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("storageLocation", storageLocation)
                .add("duration", duration)
                .add("bandwidth", bandwidth)
                .add("concurrentConnections", concurrentConnections)
                .add("waitForLock", waitForLock)
                .add("sharedContainerPath", sharedContainerPath)
                .add("cassandraDirectory", cassandraDirectory)
                .add("keyspaces", keyspaces)
                .add("snapshotTag", snapshotTag)
                .add("offlineSnapshot", offlineSnapshot)
                .add("columnFamily", columnFamily)
                .toString();
    }
}
