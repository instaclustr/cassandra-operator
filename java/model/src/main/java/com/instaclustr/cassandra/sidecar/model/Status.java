package com.instaclustr.cassandra.sidecar.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Status {
    public enum OperationMode {
        STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
    }

    public final OperationMode operationMode;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Status(@JsonProperty("operationMode") final OperationMode operationMode) {
        this.operationMode = operationMode;
    }
}
