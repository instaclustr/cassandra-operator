package com.instaclustr.cassandra.backup.common;

import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class StorageInteractor {
    public final String restoreFromClusterId;
    public final String restoreFromNodeId;
    public final String restoreFromBackupBucket;

    public StorageInteractor(final String restoreFromClusterId, final String restoreFromNodeId, final String restoreFromBackupBucket) {
        this.restoreFromClusterId = restoreFromClusterId;
        this.restoreFromNodeId = restoreFromNodeId;
        this.restoreFromBackupBucket = restoreFromBackupBucket;
    }

    public String resolveRemotePath(final Path objectKey) {
        return Paths.get(restoreFromClusterId).resolve(restoreFromNodeId).resolve(objectKey).toString();
    }

    public abstract RemoteObjectReference objectKeyToRemoteReference(final Path objectKey) throws Exception;

}
