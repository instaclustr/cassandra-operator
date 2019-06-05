package com.instaclustr.cassandra.sidecar.model;

public class Status {

    public enum OperationMode {
        STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
    }

    private OperationMode operationMode;

    private Exception exception;

    public OperationMode getOperationMode() {
        return operationMode;
    }

    public void setOperationMode(OperationMode operationMode) {
        this.operationMode = operationMode;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}
