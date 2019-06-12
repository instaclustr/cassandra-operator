package com.instaclustr.cassandra.sidecar.operations;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(OperationRequestTypeIdResolver.class)
public abstract class OperationRequest {
    // TODO: support validation?
}
