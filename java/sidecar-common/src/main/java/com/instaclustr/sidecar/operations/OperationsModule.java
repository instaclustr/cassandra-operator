package com.instaclustr.sidecar.operations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.instaclustr.guava.ServiceBindings;

public class OperationsModule extends AbstractModule {

    private final int operationsExpirationPeriod;

    public OperationsModule() {
        this(3600);
    }

    public OperationsModule(final int operationsExpirationPeriod) {
        this.operationsExpirationPeriod = operationsExpirationPeriod;
    }

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), OperationsService.class);
        ServiceBindings.bindService(binder(), OperationsExpirationService.class);

        // synchronised as this map will be used in both OperationsService and OperationsExpirationService
        // LinkedHashMap as this will preserve order in which they were added
        bind(new TypeLiteral<Map<UUID, Operation>>(){}).annotatedWith(OperationsMap.class).toInstance(Collections.synchronizedMap(new LinkedHashMap<>()));
        bind(Integer.class).annotatedWith(Names.named("operationsExpirationPeriod")).toInstance(operationsExpirationPeriod);
    }
}
