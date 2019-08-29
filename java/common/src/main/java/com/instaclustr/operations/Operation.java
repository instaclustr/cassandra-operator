package com.instaclustr.operations;

import javax.inject.Inject;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("WeakerAccess")
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(Operation.TypeIdResolver.class)
public abstract class Operation<RequestT extends OperationRequest> implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Operation.class);

    public static class TypeIdResolver extends MapBackedTypeIdResolver<Operation> {
        @Inject
        public TypeIdResolver(final Map<String, Class<? extends Operation>> typeMappings) {
            super(typeMappings);
        }
    }

    public enum State {
        PENDING, RUNNING, COMPLETED, FAILED;

        public static Set<State> TERMINAL_STATES = EnumSet.of(COMPLETED, FAILED);

        public boolean isTerminalState() {
            return TERMINAL_STATES.contains(this);
        }
    }

    public UUID id = UUID.randomUUID();
    public Instant creationTime = Instant.now();

    @JsonUnwrapped // embed the request parameters in the serialized output directly
    @JsonProperty(access = JsonProperty.Access.READ_ONLY) // added so unwrap works ok with JsonCreator
    public RequestT request;

    public State state = State.PENDING;
    public Throwable failureCause;
    public float progress = Float.NaN;
    public Instant startTime, completionTime;

    protected Operation(final RequestT request) {
        this.request = request;
    }

    protected Operation(final UUID id,
                        final Instant creationTime,
                        final State state,
                        final Throwable failureCause,
                        final float progress,
                        final Instant startTime,
                        final RequestT request) {
        this.id = id;
        this.creationTime = creationTime;
        this.state = state;
        this.failureCause = failureCause;
        this.progress = progress;
        this.startTime = startTime;
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
            logger.error(String.format("Operation %s has failed.", id), t);
            state = State.FAILED;
            failureCause = t;

        } finally {
            completionTime = Instant.now();
        }
    }

    protected abstract void run0() throws Exception;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("creationTime", creationTime)
                .add("request", request)
                .add("state", state)
                .add("failureCause", failureCause)
                .add("progress", progress)
                .add("startTime", startTime)
                .toString();
    }
}
