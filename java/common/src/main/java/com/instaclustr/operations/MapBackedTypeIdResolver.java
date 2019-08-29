package com.instaclustr.operations;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

/**
 * Performs bi-directional resolution of Types to TypeIds via the
 * provided Map<String, Class<? extends T>>
 */
public abstract class MapBackedTypeIdResolver<T> extends TypeIdResolverBase {
    private final BiMap<String, Class<? extends T>> typeMappings;

    protected MapBackedTypeIdResolver(final Map<String, Class<? extends T>> typeMappings) {
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

    public Class<? extends T> classFromId(final String id) {
        return typeMappings.get(id);
    }

    public Set<String> typeIds() {
        return Collections.unmodifiableSet(typeMappings.keySet());
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
        final Class<? extends T> clazz = classFromId(id);

        if (clazz == null) {
            return null;
        }

        return context.getTypeFactory().constructType(clazz);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
