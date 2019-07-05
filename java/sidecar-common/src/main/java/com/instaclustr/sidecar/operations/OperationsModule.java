package com.instaclustr.sidecar.operations;

import java.util.UUID;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.guava.ServiceBindings;

public class OperationsModule extends AbstractModule {

    private final String cacheSpec;

    public OperationsModule() {
        this("maximumSize=10000,expireAfterWrite=1h");
    }

    public OperationsModule(final String cacheSpec) {
        this.cacheSpec = cacheSpec;
    }

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), OperationsService.class);
    }

    @Provides
    @Singleton
    Cache<UUID, Operation> provideOperationsCache() {
        return CacheBuilder.from(cacheSpec).build();
    }
}
