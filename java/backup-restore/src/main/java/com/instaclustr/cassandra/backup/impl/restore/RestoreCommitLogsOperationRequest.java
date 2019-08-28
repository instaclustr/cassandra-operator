package com.instaclustr.cassandra.backup.impl.restore;

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
import com.instaclustr.jackson.PathDeserializer;
import com.instaclustr.jackson.PathSerializer;
import com.instaclustr.picocli.typeconverter.KeyspaceTablePairsConverter;
import com.instaclustr.picocli.typeconverter.PathTypeConverter;
import picocli.CommandLine.Option;

public class RestoreCommitLogsOperationRequest extends BaseRestoreOperationRequest {

    @Option(names = {"--dd", "--data-directory"},
            description = "Base directory that contains the Cassandra data, cache and commitlog directories",
            converter = PathTypeConverter.class,
            defaultValue = "/var/lib/cassandra/")
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraDirectory;

    @Option(names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/")
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path sharedContainerPath;

    @Option(names = {"--cd", "--config-directory"},
            description = "Directory where configuration of Cassandra is stored.",
            converter = PathTypeConverter.class,
            defaultValue = "/etc/cassandra/")
    @JsonDeserialize(using = PathDeserializer.class)
    @JsonSerialize(using = PathSerializer.class)
    public Path cassandraConfigDirectory;

    @Option(names = {"--ts", "--timestamp-start"},
            description = "When the base snapshot was taken. Only relevant if archived commitlogs are available.",
            required = true)
    @NotNull
    public long timestampStart;

    @Option(names = {"--te", "--timestamp-end"},
            description = "Point-in-time to restore up to. Only relevant if archived commitlogs are available.",
            required = true)
    @NotNull
    public long timestampEnd;

    @Option(names = {"--kt", "--keyspace-tables"},
            description = "Comma separated list of tables to restore. Must include keyspace name in the format <keyspace.table>",
            converter = KeyspaceTablePairsConverter.class)
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
                                             @JsonProperty("commitLogRestoreDirectory") final Path commitLogRestoreDirectory,
                                             @JsonProperty("timestampStart") final long timestampStart,
                                             @JsonProperty("timestampEnd") final long timestampEnd,
                                             @JsonProperty("keyspaceTables") final Multimap<String, String> keyspaceTables) {
        super(storageLocation, concurrentConnections, waitForLock);
        this.cassandraDirectory = cassandraDirectory == null ? Paths.get("/var/lib/cassandra") : cassandraDirectory;
        this.sharedContainerPath = sharedContainerPath == null ? Paths.get("/") : sharedContainerPath;
        this.cassandraConfigDirectory = cassandraConfigDirectory == null ? Paths.get("/etc/cassandra") : cassandraConfigDirectory;
        this.timestampStart = timestampStart;
        this.timestampEnd = timestampEnd;
        this.keyspaceTables = keyspaceTables;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("storageLocation", storageLocation)
                          .add("concurrentConnections", concurrentConnections)
                          .add("waitForLock", waitForLock)
                          .add("cassandraDirectory", cassandraDirectory)
                          .add("sharedContainerPath", sharedContainerPath)
                          .add("cassandraConfigDirectory", cassandraConfigDirectory)
                          .add("timestampStart", timestampStart)
                          .add("timestampEnd", timestampEnd)
                          .add("keyspaceTables", keyspaceTables)
                          .toString();
    }
}
