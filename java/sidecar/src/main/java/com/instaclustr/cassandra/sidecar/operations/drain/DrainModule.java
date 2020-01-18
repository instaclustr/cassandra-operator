package com.instaclustr.cassandra.sidecar.operations.drain;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class DrainModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "drain",
                                 DrainOperationRequest.class,
                                 DrainOperation.class);
    }
}
