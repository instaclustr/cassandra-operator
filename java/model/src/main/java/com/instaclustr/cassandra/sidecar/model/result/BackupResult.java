package com.instaclustr.cassandra.sidecar.model.result;

import java.util.UUID;

import com.instaclustr.cassandra.sidecar.model.operation.OperationType;

public class BackupResult extends OperationResult {

    public BackupResult(final UUID id) {
        this(OperationType.BACKUP, id);
    }

    public BackupResult(final OperationType type, final UUID id) {
        super(type, id);
    }

    @Override
    public String toString() {
        return "BackupResult{} " + super.toString();
    }
}
