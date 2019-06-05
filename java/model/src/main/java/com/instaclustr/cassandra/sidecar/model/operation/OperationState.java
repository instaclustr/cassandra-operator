package com.instaclustr.cassandra.sidecar.model.operation;

public enum OperationState {
    SUBMITTED,
    RUNNING,
    FINISHED,
    CRASHED
}
