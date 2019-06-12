package com.instaclustr.cassandra.sidecar.operations.decommission;

import com.google.inject.AbstractModule;

import static com.instaclustr.cassandra.sidecar.operations.OperationBindings.installOperationBindings;

public class DecommissioningModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(), "decommission", DecommissionOperationRequest.class, DecommissionOperation.class);
    }
}
