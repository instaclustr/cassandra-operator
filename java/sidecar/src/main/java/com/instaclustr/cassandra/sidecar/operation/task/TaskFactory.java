package com.instaclustr.cassandra.sidecar.operation.task;

import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;

// Besides creations of tasks, TaskFactory serves as an interface to implement for tests
// so we can bypass calls which would require running Cassandra instance
public interface TaskFactory {

    DecommissionTask createDecommissionTask(DecommissionOperation decommissionOperation);

    BackupTask createBackupTask(BackupOperation backupOperation);
}
