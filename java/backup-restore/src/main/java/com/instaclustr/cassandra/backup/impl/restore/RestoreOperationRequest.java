package com.instaclustr.cassandra.backup.impl.restore;

import javax.validation.constraints.NotBlank;
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
import com.instaclustr.jackson.PathDeserializer;
import com.instaclustr.jackson.PathSerializer;
import com.instaclustr.picocli.typeconverter.KeyspaceTablePairsConverter;
import com.instaclustr.picocli.typeconverter.PathTypeConverter;
import picocli.CommandLine.Option;

public class RestoreOperationRequest extends BaseRestoreOperationRequest {

    @Option(
            names = {"--dd", "--data-directory"},
            description = "Base directory that contains the Cassandra data, cache and commitlog directories",
            converter = PathTypeConverter.class,
            defaultValue = "/var/lib/cassandra/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraDirectory;

    @Option(
            names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path sharedContainerPath;

    @Option(
            names = {"--cd", "--config-directory"},
            description = "Directory where configuration of Cassandra is stored.",
            converter = PathTypeConverter.class,
            defaultValue = "/etc/cassandra/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraConfigDirectory;

    @Option(
            names = {"--rs", "--restore-system-keyspace"},
            description = "Restore system keyspace. Use this to prevent bootstrapping, when restoring on only a single node."
    )
    public boolean restoreSystemKeyspace;

    @Option(
            names = {"--cl-archive"},
            description = "Override path to the commit log archive directory, relative to the container root."
    )
    public Path commitLogArchiveOverride;

    @Option(
            names = {"-s", "--st", "--snapshot-tag"},
            description = "Snapshot to download and restore.",
            required = true
    )
    @NotBlank
    public String snapshotTag;

    @Option(
            names = {"--kt", "--keyspace-tables"},
            description = "Comma separated list of tables to restore. Must include keyspace name in the format <keyspace.table>",
            converter = KeyspaceTablePairsConverter.class
    )
    public Multimap<String, String> keyspaceTables = ImmutableMultimap.of();

    public RestoreOperationRequest() {
        super();
    }

    @JsonCreator
    public RestoreOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                   @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                   @JsonProperty("waitForLock") final boolean waitForLock,
                                   @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                   @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                   @JsonProperty("restoreSystemKeyspace") final boolean restoreSystemKeyspace,
                                   @JsonProperty("snapshotTag") final String snapshotTag,
                                   @JsonProperty("keyspaceTables") final Multimap<String, String> keyspaceTables) {
        super(storageLocation, concurrentConnections, waitForLock);
        this.cassandraDirectory = cassandraDirectory == null ? Paths.get("/var/lib/cassandra") : cassandraDirectory;
        this.sharedContainerPath = sharedContainerPath == null ? Paths.get("/") : sharedContainerPath;
        this.restoreSystemKeyspace = restoreSystemKeyspace;
        this.snapshotTag = snapshotTag;
        this.keyspaceTables = keyspaceTables;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("storageLocation", storageLocation)
                .add("waitForLock", waitForLock)
                .add("cassandraDirectory", cassandraDirectory)
                .add("sharedContainerPath", sharedContainerPath)
                .add("restoreSystemKeyspace", restoreSystemKeyspace)
                .add("snapshotTag", snapshotTag)
                .add("keyspaceTables", keyspaceTables)
                .toString();
    }
}
