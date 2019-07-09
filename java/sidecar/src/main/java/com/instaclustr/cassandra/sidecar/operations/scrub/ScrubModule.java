package com.instaclustr.cassandra.sidecar.operations.scrub;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;
import com.instaclustr.sidecar.operations.OperationBindings;

public class ScrubModule extends AbstractModule {
    @Override
    protected void configure() {
        OperationBindings.installOperationBindings(binder(),
                                                   CassandraOperationType.SCRUB,
                                                   ScrubOperationRequest.class,
                                                   ScrubOperation.class);
    }
}
