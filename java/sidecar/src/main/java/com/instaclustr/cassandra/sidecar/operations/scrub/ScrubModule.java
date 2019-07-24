package com.instaclustr.cassandra.sidecar.operations.scrub;

import com.google.inject.AbstractModule;
import com.instaclustr.sidecar.operations.OperationBindings;

public class ScrubModule extends AbstractModule {
    @Override
    protected void configure() {
        OperationBindings.installOperationBindings(binder(),
                                                   "scrub",
                                                   ScrubOperationRequest.class,
                                                   ScrubOperation.class);
    }
}
