package com.instaclustr.cassandra.backup.impl.backup;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationDeserializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationSerializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationTypeConverter;
import com.instaclustr.cassandra.backup.impl.StorageLocation.ValidStorageLocation;
import com.instaclustr.jackson.PathDeserializer;
import com.instaclustr.jackson.PathSerializer;
import com.instaclustr.measure.DataRate;
import com.instaclustr.measure.Time;
import com.instaclustr.picocli.typeconverter.DataRateMeasureTypeConverter;
import com.instaclustr.picocli.typeconverter.PathTypeConverter;
import com.instaclustr.picocli.typeconverter.TimeMeasureTypeConverter;
import com.instaclustr.sidecar.operations.OperationRequest;
import picocli.CommandLine;

@ValidBackupOperationRequest
public class BackupOperationRequest extends OperationRequest {

    @CommandLine.Option(
            names = {"--sl", "--storage-location"},
            converter = StorageLocationTypeConverter.class,
            description = "Location to which files will be backed up, in form " +
                    "cloudProvider://bucketName/clusterId/nodeId or file:///some/path/bucketName/clusterId/nodeId. " +
                    "'cloudProvider' is one of 's3', 'azure' or 'gcp'.",
            required = true
    )
    @NotNull
    @ValidStorageLocation
    @JsonSerialize(using = StorageLocationSerializer.class)
    @JsonDeserialize(using = StorageLocationDeserializer.class)
    public StorageLocation storageLocation;

    @CommandLine.Option(
            names = {"-d", "--duration"},
            description = "Calculate upload throughput based on total file size ÷ duration.",
            converter = TimeMeasureTypeConverter.class
    )
    public Time duration;

    @CommandLine.Option(
            names = {"-b", "--bandwidth"},
            description = "Maximum upload throughput.",
            converter = DataRateMeasureTypeConverter.class
    )
    public DataRate bandwidth;

    @CommandLine.Option(
            names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/"
    )
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    @NotNull
    public Path sharedContainerPath = Paths.get("/");

    @CommandLine.Option(
            names = {"--dd", "--data-directory"},
            description = "Base directory that contains the Cassandra data, cache and commitlog directories",
            converter = PathTypeConverter.class,
            defaultValue = "/var/lib/cassandra"
    )
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    @NotNull
    public Path cassandraDirectory;

    @CommandLine.Option(
            names = {"--cc", "--concurrent-connections"},
            description = "Number of files (or file parts) to upload concurrently. Higher values will increase throughput. Default is 10.",
            defaultValue = "10"
    )
    public Integer concurrentConnections;

    @CommandLine.Option(
            names = {"-w", "--wait"},
            description = "Wait to acquire the global transfer lock (which prevents more than one backup or restore from running)."
    )
    public boolean waitForLock;

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
    }

    @JsonCreator
    public BackupOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                  @JsonProperty("duration") final Time duration,
                                  @JsonProperty("bandwidth") final DataRate bandwidth,
                                  @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                  @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                  @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                  @JsonProperty("waitForLock") final Boolean waitForLock,
                                  @JsonProperty("keyspaces") final List<String> keyspaces,
                                  @JsonProperty("snapshotTag") final String snapshotTag,
                                  @JsonProperty("offlineSnapshot") final boolean offlineSnapshot,
                                  @JsonProperty("columnFamily") final String columnFamily) {
        this.storageLocation = storageLocation;
        this.duration = duration;
        this.bandwidth = bandwidth;
        this.sharedContainerPath = sharedContainerPath == null ? Paths.get("/") : sharedContainerPath;
        this.cassandraDirectory = cassandraDirectory == null ? Paths.get("/var/lib/cassandra") : cassandraDirectory;
        this.concurrentConnections = concurrentConnections == null ? 10 : concurrentConnections;
        this.waitForLock = waitForLock == null ? true : waitForLock;
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
                .add("sharedContainerPath", sharedContainerPath)
                .add("cassandraDirectory", cassandraDirectory)
                .add("concurrentConnections", concurrentConnections)
                .add("waitForLock", waitForLock)
                .add("keyspaces", keyspaces)
                .add("snapshotTag", snapshotTag)
                .add("offlineSnapshot", offlineSnapshot)
                .add("columnFamily", columnFamily)
                .toString();
    }
}
