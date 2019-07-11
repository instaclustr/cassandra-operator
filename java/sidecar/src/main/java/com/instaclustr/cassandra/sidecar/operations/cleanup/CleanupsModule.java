package com.instaclustr.cassandra.sidecar.operations.cleanup;


import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;

public class CleanupsModule extends AbstractModule {

    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 CassandraOperationType.CLEANUP,
                                 CleanupOperationRequest.class,
                                 CleanupOperation.class);
    }
}
