package com.instaclustr.cassandra.sidecar.operations;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

// TODO: type resolver for Operations
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class Operation<RequestT extends OperationRequest> implements Runnable {
    enum State {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public final UUID id = UUID.randomUUID();
    public final Instant creationTime = Instant.now();

    // TODO: include and unwrap in JSON output
    protected final RequestT request;

    public State state = State.PENDING;
    public Throwable failureCause;
    public float progress = Float.NaN;
    public Instant startTime, completionTime;

    protected Operation(final RequestT request) {
        this.request = request;
    }

    @Override
    public final void run() {
        state = State.RUNNING;
        startTime = Instant.now();

        try {
            run0();
            progress = 1;
            state = State.COMPLETED;

        } catch (final Throwable t) {
            // TODO: log exception
            state = State.FAILED;
            failureCause = t;

        } finally {
            completionTime = Instant.now();
        }


    }

    protected abstract void run0() throws Exception;
}
