package com.instaclustr.operations;

import com.google.inject.AbstractModule;
import com.instaclustr.guice.ServiceBindings;

public class OperationsModule extends AbstractModule {

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), OperationsService.class);
    }

}
