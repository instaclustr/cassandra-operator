package com.instaclustr.cassandra.sidecar.model.operation;

import java.util.UUID;

public abstract class Operation {

    private OperationType type;

    private UUID id;

    public Operation() {
    }

    public Operation(final OperationType type) {
        this.type = type;
        this.id = UUID.randomUUID();
    }

    public Operation(final OperationType type, final UUID id) {
        this.type = type;
        this.id = id;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
