package com.instaclustr.cassandra.backup.impl.backup;

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.measure.DataRate;
import com.instaclustr.measure.Time;
import picocli.CommandLine.Option;

@ValidBackupCommitLogsOperationRequest
public class BackupCommitLogsOperationRequest extends BaseBackupOperationRequest {

    @Option(names = {"--cl-archive"},
            description = "Override path to the commitlog archive directory, relative to the container root.")
    public Path commitLogArchiveOverride;

    public BackupCommitLogsOperationRequest() {
        // for picocli
    }

    @JsonCreator
    public BackupCommitLogsOperationRequest(@JsonProperty("storageLocation") final StorageLocation storageLocation,
                                            @JsonProperty("duration") final Time duration,
                                            @JsonProperty("bandwidth") final DataRate bandwidth,
                                            @JsonProperty("concurrentConnections") final Integer concurrentConnections,
                                            @JsonProperty("waitForLock") final boolean waitForLock,
                                            @JsonProperty("sharedContainerPath") final Path sharedContainerPath,
                                            @JsonProperty("cassandraDirectory") final Path cassandraDirectory,
                                            @JsonProperty("commitLogRestoreDirectory") final Path commitLogArchiveOverride) {
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
                .add("commitLogRestoreDirectory", commitLogArchiveOverride)
                .toString();
    }
}
