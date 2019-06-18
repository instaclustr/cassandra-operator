package com.instaclustr.sidecar.operations;

import com.google.inject.AbstractModule;
import com.instaclustr.guava.ServiceBindings;

public class OperationsModule extends AbstractModule {

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), OperationsService.class);
    }

}
