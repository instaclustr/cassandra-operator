package com.instaclustr.cassandra.sidecar.operations.flush;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class FlushModule extends AbstractModule {

    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "flush",
                                 FlushOperationRequest.class,
                                 FlushOperation.class);
    }
}
