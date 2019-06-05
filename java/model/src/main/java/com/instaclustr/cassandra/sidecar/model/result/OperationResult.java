package com.instaclustr.cassandra.sidecar.model.result;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.operation.OperationState;
import com.instaclustr.cassandra.sidecar.model.operation.OperationType;

public abstract class OperationResult extends Operation {

    private Duration duration;

    private Instant start;

    private Instant stop;

    private OperationState operationState;

    private Exception exception;

    public OperationResult(final OperationType type) {
        super(type);
    }

    public OperationResult(final OperationType type, final UUID id) {
        super(type, id);
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public OperationState getOperationState() {
        return operationState;
    }

    public Throwable getException() {
        return exception;
    }

    public boolean hasFailed() {
        return exception != null;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public void setOperationState(final OperationState operationState) {
        this.operationState = operationState;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getStop() {
        return stop;
    }

    public void setStop(Instant stop) {
        this.stop = stop;
    }

    @Override
    public String toString() {
        return "OperationResult{" +
                "duration=" + duration +
                ", start=" + start +
                ", stop=" + stop +
                ", operationState=" + operationState +
                ", exception=" + exception +
                "} " + super.toString();
    }
}
