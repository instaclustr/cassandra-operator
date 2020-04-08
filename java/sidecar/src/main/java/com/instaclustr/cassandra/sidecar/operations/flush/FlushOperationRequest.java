package com.instaclustr.cassandra.sidecar.operations.flush;

import javax.validation.constraints.NotNull;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.operations.OperationRequest;

public class FlushOperationRequest extends OperationRequest {

    @NotNull
    public String keyspace;

    public Set<String> tables;

    @JsonCreator
    public FlushOperationRequest(@JsonProperty("keyspace") final String keyspace,
                                 @JsonProperty("tables") final Set<String> tables) {
        this.keyspace = keyspace;
        this.tables = tables;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("keyspace", keyspace)
            .add("tables", tables)
            .toString();
    }
}
