package com.instaclustr.cassandra.sidecar.operations.cleanup;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.instaclustr.cassandra.sidecar.operations.Operation;
import com.instaclustr.cassandra.sidecar.operations.OperationFactory;
import com.instaclustr.cassandra.sidecar.operations.OperationRequest;

public class CleanupsModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(Operation.class, CleanupOperation.class)
                .build(new TypeLiteral<OperationFactory<CleanupOperationRequest>>() {}));

        {
            final MapBinder<String, Class<? extends OperationRequest>> requestTypeBinder = MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<Class<? extends OperationRequest>>() {});
            requestTypeBinder.addBinding("cleanup").toInstance(CleanupOperationRequest.class);
        }

        {
            final MapBinder<Class<? extends OperationRequest>, OperationFactory> operationTypeBinder = MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends OperationRequest>>() {}, TypeLiteral.get(OperationFactory.class));
            operationTypeBinder.addBinding(CleanupOperationRequest.class).to(new TypeLiteral<OperationFactory<CleanupOperationRequest>>() {});
        }
    }
}
