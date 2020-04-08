package com.instaclustr.cassandra.sidecar.operations.refresh;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class RefreshModule extends AbstractModule {

    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "refresh",
                                 RefreshOperationRequest.class,
                                 RefreshOperation.class);
    }
}
