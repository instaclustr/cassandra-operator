package com.instaclustr.operations;

import javax.inject.Inject;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(OperationRequest.TypeIdResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class OperationRequest {

    static class TypeIdResolver extends MapBackedTypeIdResolver<OperationRequest> {
        @Inject
        public TypeIdResolver(final Map<String, Class<? extends OperationRequest>> typeMappings) {
            super(typeMappings);
        }
    }
}
