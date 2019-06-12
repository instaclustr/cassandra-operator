package com.instaclustr.cassandra.sidecar.operations.decommission;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.instaclustr.cassandra.sidecar.operations.Operation;
import com.instaclustr.cassandra.sidecar.operations.OperationFactory;
import com.instaclustr.cassandra.sidecar.operations.OperationRequest;
public class DecommissioningModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(Operation.class, DecommissionOperation.class)
                .build(new TypeLiteral<OperationFactory<DecommissionOperationRequest>>() {}));

        {
            final MapBinder<String, Class<? extends OperationRequest>> requestTypeBinder = MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<Class<? extends OperationRequest>>() {});
            requestTypeBinder.addBinding("decommission").toInstance(DecommissionOperationRequest.class);
        }

        {
            final MapBinder<Class<? extends OperationRequest>, OperationFactory> operationTypeBinder = MapBinder.newMapBinder(binder(), new TypeLiteral<Class<? extends OperationRequest>>() {}, TypeLiteral.get(OperationFactory.class));
            operationTypeBinder.addBinding(DecommissionOperationRequest.class).to(new TypeLiteral<OperationFactory<DecommissionOperationRequest>>() {});
        }
    }
}
