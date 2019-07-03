package com.instaclustr.cassandra.sidecar.operations.decommission;

import static com.instaclustr.cassandra.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class DecommissioningModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(), "decommission", DecommissionOperationRequest.class, DecommissionOperation.class);
    }
}
