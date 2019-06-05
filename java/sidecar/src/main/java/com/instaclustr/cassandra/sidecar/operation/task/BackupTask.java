package com.instaclustr.cassandra.sidecar.operation.task;

import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.result.BackupResult;
import com.instaclustr.cassandra.sidecar.operation.OperationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupTask extends OperationTask<BackupOperation, BackupResult> {

    private static final Logger logger = LoggerFactory.getLogger(BackupTask.class);

    public BackupTask(BackupOperation operation) {
        super(operation, new BackupResult(operation.getId()));
    }

    @Override
    protected void executeTask(final BackupOperation operation, final BackupResult result) throws Exception {
        // TODO
    }
}
