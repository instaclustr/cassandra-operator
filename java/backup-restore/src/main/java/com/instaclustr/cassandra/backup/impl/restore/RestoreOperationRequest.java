package com.instaclustr.cassandra.backup.impl.restore;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationDeserializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationSerializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.ValidStorageLocation;
import com.instaclustr.jackson.PathDeserializer;
import com.instaclustr.jackson.PathSerializer;
import com.instaclustr.picocli.typeconverter.KeyspaceTablePairsConverter;
import com.instaclustr.picocli.typeconverter.PathTypeConverter;
import com.instaclustr.sidecar.operations.OperationRequest;
import picocli.CommandLine;

public class RestoreOperationRequest extends OperationRequest {

    @CommandLine.Option(
            names = {"--sl", "--storage-location"},
            converter = StorageLocation.StorageLocationTypeConverter.class,
            description = "Location from which files will be fetched for restore, in form " +
                    "cloudProvider://bucketName/clusterId/nodeId or file:///some/path/bucketName/clusterId/nodeId. " +
                    "'cloudProvider' is one of 'aws', 'azure' or 'gcp'.",
            required = true
    )
    @NotNull
    @ValidStorageLocation
    @JsonSerialize(using = StorageLocationSerializer.class)
    @JsonDeserialize(using = StorageLocationDeserializer.class)
    public StorageLocation storageLocation;

    @CommandLine.Option(
            names = {"--dd", "--data-directory"},
            description = "Base directory that contains the Cassandra data, cache and commitlog directories",
            converter = PathTypeConverter.class,
            defaultValue = "/var/lib/cassandra/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraDirectory;

    @CommandLine.Option(
            names = {"--cd", "--config-directory"},
            description = "Directory where configuration of Cassandra is stored.",
            converter = PathTypeConverter.class,
            defaultValue = "/etc/cassandra/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraConfigDirectory;

    @CommandLine.Option(
            names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path sharedContainerPath;

    @CommandLine.Option(
            names = {"--kt", "--keyspace-tables"},
            description = "Comma separated list of tables to restore. Must include keyspace name in the format <keyspace.table>",
            converter = KeyspaceTablePairsConverter.class
    )
    public Multimap<String, String> keyspaceTables;

    @CommandLine.Option(
            names = {"-s", "--st", "--snapshot-tag"},
            description = "Snapshot to download and restore.",
            required = true
    )
    @NotBlank
    public String snapshotTag;

    @CommandLine.Option(
            names = {"--rs", "--restore-system-keyspace"},
            description = "Restore system keyspace. Use this to prevent bootstrapping, when restoring on only a single node."
    )
    public boolean restoreSystemKeyspace;

    @CommandLine.Option(
            names = {"-w", "--wait"},
            description = "Wait to acquire the global transfer lock (which prevents more than one backup or restore from running)."
    )
    public Boolean waitForLock;

    @CommandLine.Option(
            names = {"--cc", "--concurrent-connections"},
            description = "Number of files (or file parts) to download concurrently. Higher values will increase throughput. Default is 10.",
            defaultValue = "10"
    )
    public Integer concurrentConnections;

    public RestoreOperationRequest() {
    }

    @JsonCreator
    public RestoreOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                   @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                   @JsonProperty("cassandraConfigDirectory") final Path cassandraConfigDirectory,
                                   @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                   @JsonProperty("keyspaceTables") final Multimap<String, String> keyspaceTables,
                                   @JsonProperty("snapshotTag") final String snapshotTag,
                                   @JsonProperty("restoreSystemKeyspace") final boolean restoreSystemKeyspace,
                                   @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                   @JsonProperty("waitForLock") final Boolean waitForLock) {
        this.storageLocation = storageLocation;
        this.cassandraDirectory = cassandraDirectory == null ? Paths.get("/var/lib/cassandra") : cassandraDirectory;
        this.cassandraConfigDirectory = cassandraConfigDirectory == null ? Paths.get("/etc/cassandra") : cassandraConfigDirectory;
        this.sharedContainerPath = sharedContainerPath == null ? Paths.get("/") : sharedContainerPath;
        this.keyspaceTables = keyspaceTables == null ? ImmutableMultimap.of() : keyspaceTables;
        this.snapshotTag = snapshotTag;
        this.restoreSystemKeyspace = restoreSystemKeyspace;
        this.concurrentConnections = concurrentConnections == null ? 10 : concurrentConnections;
        this.waitForLock = waitForLock == null ? true : waitForLock;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("storageLocation", storageLocation)
                .add("cassandraDirectory", cassandraDirectory)
                .add("cassandraConfigDirectory", cassandraConfigDirectory)
                .add("sharedContainerPath", sharedContainerPath)
                .add("keyspaceTables", keyspaceTables)
                .add("snapshotTag", snapshotTag)
                .add("restoreSystemKeyspace", restoreSystemKeyspace)
                .add("concurrentConnections", concurrentConnections)
                .add("waitForLock", waitForLock)
                .toString();
    }
}
