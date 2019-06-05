package com.instaclustr.cassandra.sidecar.model.operation;

import static com.instaclustr.cassandra.sidecar.model.operation.OperationType.BACKUP;

public class BackupOperation extends Operation {

    public BackupOperation() {
        super(BACKUP);
    }
}
