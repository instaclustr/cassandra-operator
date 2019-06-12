package com.instaclustr.cassandra.sidecar.operations;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.inject.Injector;
import com.instaclustr.cassandra.sidecar.operations.backup.BackupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissionOperationRequest;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

class OperationRequestTypeIdResolver extends TypeIdResolverBase {
    private final BiMap<String, Class<? extends OperationRequest>> typeMappings;

    @Inject
    public OperationRequestTypeIdResolver(final Map<String, Class<? extends OperationRequest>> typeMappings) {
        this.typeMappings = ImmutableBiMap.copyOf(typeMappings);
    }


    @Override
    public String idFromValue(final Object value) {
        return idFromValueAndType(value, value.getClass());
    }

    @Override
    public String idFromValueAndType(final Object value, final Class<?> suggestedType) {
        return typeMappings.inverse().get(suggestedType);
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        final Class<? extends OperationRequest> requestClass = typeMappings.get(id);

        if (requestClass == null) {
            return null;
        }

        return context.getTypeFactory().constructType(requestClass);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
