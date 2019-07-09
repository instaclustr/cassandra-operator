package com.instaclustr.cassandra.sidecar.operations.decommission;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;

public class DecommissioningModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 CassandraOperationType.DECOMMISSION,
                                 DecommissionOperationRequest.class,
                                 DecommissionOperation.class);
    }
}
