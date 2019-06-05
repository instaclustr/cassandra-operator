package com.instaclustr.cassandra.sidecar.model.result;

import java.util.UUID;

import com.instaclustr.cassandra.sidecar.model.operation.OperationType;

public class DecommissionResult extends OperationResult {

    public DecommissionResult(final UUID id) {
        this(OperationType.DECOMMISSION, id);
    }

    public DecommissionResult(final OperationType type, final UUID id) {
        super(type, id);
    }

    @Override
    public String toString() {
        return "DecommissionResult{} " + super.toString();
    }
}
