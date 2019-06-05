package com.instaclustr.cassandra.sidecar.model.operation;

import static com.instaclustr.cassandra.sidecar.model.operation.OperationType.DECOMMISSION;

public class DecommissionOperation extends Operation {

    public DecommissionOperation() {
        super(DECOMMISSION);
    }
}
