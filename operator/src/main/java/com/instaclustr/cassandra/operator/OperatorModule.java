package com.instaclustr.cassandra.operator;

import com.instaclustr.cassandra.operator.service.ControllerService;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.service.GarbageCollectorService;

public class OperatorModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(ControllerService.class);
        serviceMultibinder.addBinding().to(GarbageCollectorService.class);
    }
}
