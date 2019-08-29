package com.instaclustr.cassandra.sidecar.operations.rebuild;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class RebuildModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "rebuild",
                                 RebuildOperationRequest.class,
                                 RebuildOperation.class);
    }
}
