package com.instaclustr.operations;

import static java.util.concurrent.TimeUnit.HOURS;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.instaclustr.guice.ServiceBindings;
import com.instaclustr.measure.Time;

public class OperationsModule extends AbstractModule {

    private static final Time DEFAULT_OPERATIONS_EXPIRATION_PERIOD = new Time((long) 1, HOURS);

    private final long operationsExpirationPeriod;

    public OperationsModule() {
        this(DEFAULT_OPERATIONS_EXPIRATION_PERIOD);
    }

    public OperationsModule(final Time operationsExpirationPeriod) {
        this(operationsExpirationPeriod.asSeconds().value);
    }

    public OperationsModule(final long operationsExpirationPeriod) {
        this.operationsExpirationPeriod = operationsExpirationPeriod;
    }

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), OperationsService.class);
        ServiceBindings.bindService(binder(), OperationsExpirationService.class);

        // synchronised as this map will be used in both OperationsService and OperationsExpirationService
        // LinkedHashMap as this will preserve order in which they were added
        bind(new TypeLiteral<Map<UUID, Operation>>() {}).annotatedWith(OperationsMap.class).toInstance(Collections.synchronizedMap(new LinkedHashMap<>()));
        bind(Long.class).annotatedWith(Names.named("operationsExpirationPeriod")).toInstance(operationsExpirationPeriod);
    }
}
