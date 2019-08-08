package com.instaclustr.cassandra.backup.impl.backup;

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.measure.DataRate;
import com.instaclustr.measure.Time;
import picocli.CommandLine;

@ValidBackupCommitLogsOperationRequest
public class BackupCommitLogsOperationRequest extends BaseBackupOperationRequest {

    @CommandLine.Option(
            names = {"--cl-archive"},
            description = "Override path to the commitlog archive directory, relative to the container root."
    )
    public Path commitLogArchiveOverride;

    public BackupCommitLogsOperationRequest() {
        super();
    }

    @JsonCreator
    public BackupCommitLogsOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                            @JsonProperty("duration") final Time duration,
                                            @JsonProperty("bandwidth") final DataRate bandwidth,
                                            @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                            @JsonProperty("waitForLock") final Boolean waitForLock,
                                            @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                            @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                            @JsonProperty("commitLogArchiveOverride") final Path commitLogArchiveOverride) {
        super(storageLocation, duration, bandwidth, concurrentConnections, waitForLock, sharedContainerPath, cassandraDirectory);
        this.commitLogArchiveOverride = commitLogArchiveOverride;
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
                .add("commitLogArchiveOverride", commitLogArchiveOverride)
                .toString();
    }
}
