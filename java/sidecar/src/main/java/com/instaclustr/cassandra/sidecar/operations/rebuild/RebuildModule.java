package com.instaclustr.cassandra.sidecar.operations.rebuild;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;

public class RebuildModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 CassandraOperationType.REBUILD,
                                 RebuildOperationRequest.class,
                                 RebuildOperation.class);
    }
}
