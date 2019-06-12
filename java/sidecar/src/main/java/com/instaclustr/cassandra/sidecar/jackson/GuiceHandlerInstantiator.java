package com.instaclustr.cassandra.sidecar.jackson;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.google.inject.Injector;

import javax.inject.Inject;

/**
 * A HandlerInstantiator that forwards requests to Guice, to allow for injection
 * into handlers
 */
public class GuiceHandlerInstantiator extends HandlerInstantiator {
    private final Injector injector;

    @Inject
    public GuiceHandlerInstantiator(final Injector injector) {
        this.injector = injector;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JsonDeserializer<?> deserializerInstance(final DeserializationConfig config, final Annotated annotated, final Class<?> deserClass) {
        return injector.getInstance((Class<? extends JsonDeserializer>)deserClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public KeyDeserializer keyDeserializerInstance(final DeserializationConfig config, final Annotated annotated, final Class<?> keyDeserClass) {
        return injector.getInstance((Class<? extends KeyDeserializer>)keyDeserClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JsonSerializer<?> serializerInstance(final SerializationConfig config, final Annotated annotated, final Class<?> serClass) {
        return injector.getInstance((Class<? extends JsonSerializer>)serClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public TypeResolverBuilder<?> typeResolverBuilderInstance(final MapperConfig<?> config, final Annotated annotated, final Class<?> builderClass) {
        return injector.getInstance((Class<? extends TypeResolverBuilder>)builderClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public TypeIdResolver typeIdResolverInstance(final MapperConfig<?> config, final Annotated annotated, final Class<?> resolverClass) {
        return injector.getInstance((Class<? extends TypeIdResolver>)resolverClass);
    }
}
