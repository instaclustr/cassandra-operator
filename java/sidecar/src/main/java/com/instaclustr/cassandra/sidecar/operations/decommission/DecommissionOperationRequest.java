package com.instaclustr.cassandra.sidecar.operations.decommission;


import com.google.common.base.MoreObjects;
import com.instaclustr.sidecar.operations.OperationRequest;

public class DecommissionOperationRequest extends OperationRequest {
    // decommission requests have no parameters

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("type", type).toString();
    }
}
