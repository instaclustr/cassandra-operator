package com.instaclustr.cassandra.sidecar.operation.task;

import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;

public class DefaultTaskFactory implements TaskFactory {

    @Override
    public DecommissionTask createDecommissionTask(DecommissionOperation decommissionOperation) {
        return new DecommissionTask(decommissionOperation);
    }

    @Override
    public BackupTask createBackupTask(BackupOperation backupOperation) {
        return new BackupTask(backupOperation);
    }
}
