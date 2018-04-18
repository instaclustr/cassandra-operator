package com.instaclustr.cassandra.operator.preflight;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.preflight.operations.CreateCustomResourceDefinitions;
import com.instaclustr.cassandra.operator.K8sVersionValidator;

public class PreflightModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<Operation> operationMultibinder = Multibinder.newSetBinder(binder(), Operation.class);

        operationMultibinder.addBinding().to(CreateCustomResourceDefinitions.class);
    }
}
