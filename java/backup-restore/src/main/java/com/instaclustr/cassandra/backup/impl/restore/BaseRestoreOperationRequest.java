package com.instaclustr.cassandra.backup.impl.restore;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.instaclustr.cassandra.backup.impl.StorageLocation;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationDeserializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationSerializer;
import com.instaclustr.cassandra.backup.impl.StorageLocation.StorageLocationTypeConverter;
import com.instaclustr.cassandra.backup.impl.StorageLocation.ValidStorageLocation;
import com.instaclustr.sidecar.operations.OperationRequest;
import picocli.CommandLine;

public class BaseRestoreOperationRequest extends OperationRequest {

    @CommandLine.Option(
            names = {"--sl", "--storage-location"},
            converter = StorageLocationTypeConverter.class,
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
            names = {"--cc", "--concurrent-connections"},
            description = "Number of files (or file parts) to download concurrently. Higher values will increase throughput. Default is 10.",
            defaultValue = "10"
    )
    public Integer concurrentConnections = 10;

    @CommandLine.Option(
            names = {"-w", "--waitForLock"},
            description = "Wait to acquire the global transfer lock (which prevents more than one backup or restore from running)."
    )
    public boolean waitForLock = true;

    public BaseRestoreOperationRequest() {
        // for picocli
    }

    public BaseRestoreOperationRequest(final StorageLocation storageLocation,
                                       final Integer concurrentConnections,
                                       final boolean waitForLock) {
        this.storageLocation = storageLocation;
        this.concurrentConnections = concurrentConnections;
        this.waitForLock = waitForLock;
    }
}
