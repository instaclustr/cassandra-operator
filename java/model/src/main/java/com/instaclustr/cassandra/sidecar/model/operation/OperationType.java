package com.instaclustr.cassandra.sidecar.model.operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OperationType {
    BACKUP,
    DECOMMISSION,
    UPGRADESSTABLES,
    CLEANUP;

    @JsonCreator
    public static OperationType forValue(String value) {
        return OperationType.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return this.name().toLowerCase();
    }
}
