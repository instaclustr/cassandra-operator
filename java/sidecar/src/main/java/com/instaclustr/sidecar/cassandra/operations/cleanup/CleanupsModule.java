package com.instaclustr.sidecar.cassandra.operations.cleanup;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class CleanupsModule extends AbstractModule {

    @Override
    protected void configure() {
        installOperationBindings(binder(), "cleanup", CleanupOperationRequest.class, CleanupOperation.class);
    }
}
