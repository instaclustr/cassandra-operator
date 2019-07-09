package com.instaclustr.sidecar.jackson;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.instaclustr.sidecar.operations.OperationType;

/**
 * Performs bi-directional resolution of Types to TypeIds via the
 * provided Map<String, Class<? extends T>>
 */
public abstract class MapBackedTypeIdResolver<T> extends TypeIdResolverBase {
    private final BiMap<OperationType, Class<? extends T>> typeMappings;

    protected MapBackedTypeIdResolver(final Map<OperationType, Class<? extends T>> typeMappings) {
        this.typeMappings = ImmutableBiMap.copyOf(typeMappings);
    }

    @Override
    public String idFromValue(final Object value) {
        return idFromValueAndType(value, value.getClass());
    }

    @Override
    public String idFromValueAndType(final Object value, final Class<?> suggestedType) {
        return typeMappings.inverse().get(suggestedType).toString();
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {

        if (id == null) {
            return null;
        }

        final OperationType operationType = typeMappings.keySet().stream().filter(type -> type.toString().toLowerCase().equals(id.toLowerCase())).findFirst().orElse(null);

        if (operationType == null) {
            return null;
        }

        final Class<? extends T> requestClass = typeMappings.get(operationType);

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
