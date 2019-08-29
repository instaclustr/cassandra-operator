package com.instaclustr.cassandra.backup.impl.backup;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
import com.instaclustr.operations.OperationRequest;
import picocli.CommandLine.Option;

public class BaseBackupOperationRequest extends OperationRequest {

    @Option(names = {"--sl", "--storage-location"},
            converter = StorageLocationTypeConverter.class,
            description = "Location to which files will be backed up, in form " +
                    "cloudProvider://bucketName/clusterId/nodeId or file:///some/path/bucketName/clusterId/nodeId. " +
                    "'cloudProvider' is one of 's3', 'azure' or 'gcp'.",
            required = true)
    @NotNull
    @ValidStorageLocation
    @JsonSerialize(using = StorageLocationSerializer.class)
    @JsonDeserialize(using = StorageLocationDeserializer.class)
    public StorageLocation storageLocation;

    @Option(names = {"--dd", "--data-directory"},
            description = "Base directory that contains the Cassandra data, cache and commitlog directories",
            converter = PathTypeConverter.class,
            defaultValue = "/var/lib/cassandra")
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    @NotNull
    public Path cassandraDirectory;

    @Option(names = {"-p", "--shared-path"},
            description = "Shared Container path for pod",
            converter = PathTypeConverter.class,
            defaultValue = "/")
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    @NotNull
    public Path sharedContainerPath = Paths.get("/");

    @Option(names = {"-d", "--duration"},
            description = "Calculate upload throughput based on total file size ÷ duration.",
            converter = TimeMeasureTypeConverter.class)
    public Time duration;

    @Option(names = {"-b", "--bandwidth"},
            description = "Maximum upload throughput.",
            converter = DataRateMeasureTypeConverter.class)
    public DataRate bandwidth;

    @Option(names = {"--cc", "--concurrent-connections"},
            description = "Number of files (or file parts) to upload concurrently. Higher values will increase throughput. Default is 10.",
            defaultValue = "10")
    public Integer concurrentConnections;

    @Option(names = {"-w", "--waitForLock"},
            description = "Wait to acquire the global transfer lock (which prevents more than one backup or restore from running).")
    public Boolean waitForLock = true;

    public BaseBackupOperationRequest() {
        // for picocli
    }

    public BaseBackupOperationRequest(final StorageLocation storageLocation,
                                      final Time duration,
                                      final DataRate bandwidth,
                                      final Integer concurrentConnections,
                                      final boolean waitForLock,
                                      final Path cassandraDirectory,
                                      final Path sharedContainerPath) {
        this.storageLocation = storageLocation;
        this.duration = duration;
        this.bandwidth = bandwidth;
        this.sharedContainerPath = sharedContainerPath == null ? Paths.get("/") : sharedContainerPath;
        this.cassandraDirectory = cassandraDirectory == null ? Paths.get("/var/lib/cassandra") : cassandraDirectory;
        this.concurrentConnections = concurrentConnections == null ? 10 : concurrentConnections;
        this.waitForLock = waitForLock;
    }
}
