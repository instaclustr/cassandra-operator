package com.instaclustr.cassandra.backup.impl.restore;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.jackson.PathDeserializer;
import com.instaclustr.jackson.PathSerializer;
import com.instaclustr.picocli.typeconverter.KeyspaceTablePairsConverter;
import com.instaclustr.picocli.typeconverter.PathTypeConverter;
import picocli.CommandLine;

public class RestoreCommitLogsOperationRequest extends BaseRestoreOperationRequest {

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
            names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/"
    )
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path sharedContainerPath;

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
            names = {"--cl-archive"},
            description = "Override path to the commit log archive directory, relative to the container root."
    )
    public Path commitLogArchiveOverride;

    @CommandLine.Option(
            names = {"--ts", "--timestamp-start"},
            description = "When the base snapshot was taken. Only relevant if archived commitlogs are available.",
            required = true
    )
    @NotNull
    public long timestampStart;

    @CommandLine.Option(
            names = {"--te", "--timestamp-end"},
            description = "Point-in-time to restore up to. Only relevant if archived commitlogs are available.",
            required = true
    )
    @NotNull
    public long timestampEnd;

    @CommandLine.Option(
            names = {"--kt", "--keyspace-tables"},
            description = "Comma separated list of tables to restore. Must include keyspace name in the format <keyspace.table>",
            converter = KeyspaceTablePairsConverter.class
    )
    public Multimap<String, String> keyspaceTables = ImmutableMultimap.of();

    public RestoreCommitLogsOperationRequest() {
        // for picocli
    }

    @JsonCreator
    public RestoreCommitLogsOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                             @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                             @JsonProperty("waitForLock") final Boolean waitForLock,
                                             @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                             @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                             @JsonProperty("cassandraConfigDirectory") final Path cassandraConfigDirectory,
                                             @JsonProperty("commitLogArchiveOverride") final Path commitLogArchiveOverride,
                                             @JsonProperty("timestampStart") final long timestampStart,
                                             @JsonProperty("timestampEnd") final long timestampEnd,
                                             @JsonProperty("keyspaceTables") final Multimap<String, String> keyspaceTables) {
        super(storageLocation, concurrentConnections, waitForLock);
        this.cassandraDirectory = cassandraDirectory;
        this.sharedContainerPath = sharedContainerPath;
        this.cassandraConfigDirectory = cassandraConfigDirectory;
        this.commitLogArchiveOverride = commitLogArchiveOverride;
        this.timestampStart = timestampStart;
        this.timestampEnd = timestampEnd;
        this.keyspaceTables = keyspaceTables;
    }
}
