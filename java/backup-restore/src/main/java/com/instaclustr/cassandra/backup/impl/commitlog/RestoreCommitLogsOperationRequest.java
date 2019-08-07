package com.instaclustr.cassandra.backup.impl.commitlog;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Multimap;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import picocli.CommandLine;

public class RestoreCommitLogsOperationRequest extends RestoreOperationRequest {

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

    public RestoreCommitLogsOperationRequest() {
        // for picocli
    }

    @JsonCreator
    public RestoreCommitLogsOperationRequest(@JsonProperty("commitLogArchiveOverride") Path commitLogArchiveOverride,
                                             @JsonProperty("timestampStart") long timestampStart,
                                             @JsonProperty("timestampEnd") long timestampEnd,
                                             @JsonProperty("storageLocation") final StorageLocation storageLocation,
                                             @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                             @JsonProperty("cassandraConfigDirectory") final Path cassandraConfigDirectory,
                                             @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                             @JsonProperty("keyspaceTables") final Multimap<String, String> keyspaceTables,
                                             @JsonProperty("snapshotTag") final String snapshotTag,
                                             @JsonProperty("restoreSystemKeyspace") final boolean restoreSystemKeyspace,
                                             @JsonProperty("waitForLock") final Boolean waitForLock,
                                             @JsonProperty("concurrentConnections") final Integer concurrentConnections) {
        super(storageLocation,
              cassandraDirectory,
              cassandraConfigDirectory,
              sharedContainerPath,
              keyspaceTables,
              snapshotTag,
              restoreSystemKeyspace,
              concurrentConnections,
              waitForLock);
        this.commitLogArchiveOverride = commitLogArchiveOverride;
        this.timestampStart = timestampStart;
        this.timestampEnd = timestampEnd;
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
                .add("waitForLock", waitForLock)
                .add("concurrentConnections", concurrentConnections)
                .add("commitLogArchiveOverride", commitLogArchiveOverride)
                .add("timestampStart", timestampStart)
                .add("timestampEnd", timestampEnd)
                .toString();
    }
}
