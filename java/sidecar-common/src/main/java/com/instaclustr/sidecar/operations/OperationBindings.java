package com.instaclustr.sidecar.operations;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Types;
import com.instaclustr.sidecar.jackson.MapBackedTypeIdResolver;

public final class OperationBindings {
    private OperationBindings() {
    }

    /**
     * Install the various bindings and AssistedInject {@link OperationFactory}s for
     * a {@link Operation} and its associated {@link OperationRequest} type.
     * <p>
     * This allows for automatic JSON serialization/deserialization (via {@link MapBackedTypeIdResolver}),
     * and creation of Operations from their Requests.
     */
    public static <RequestT extends OperationRequest, OperationT extends Operation<RequestT>>
    void installOperationBindings(final Binder binder,
                                  final OperationType typeId,
                                  final Class<RequestT> requestClass,
                                  final Class<OperationT> operationClass) {

        @SuppressWarnings("unchecked") final TypeLiteral<OperationFactory<RequestT>> operationFactoryType =
                (TypeLiteral<OperationFactory<RequestT>>) TypeLiteral.get(
                        Types.newParameterizedType(OperationFactory.class, requestClass)
                );


        final TypeLiteral<Class<? extends OperationRequest>> operationRequestClassType = new TypeLiteral<Class<? extends OperationRequest>>() {};
        final TypeLiteral<Class<? extends Operation>> operationClassType = new TypeLiteral<Class<? extends Operation>>() {};

        // get Guice to create the AssistedInject OperationFactory implementation for the Operation class
        // to allow creation Operations from their OperationRequests
        binder.install(new FactoryModuleBuilder()
                               .implement(Operation.class, operationClass)
                               .build(operationFactoryType));

        // add an entry to the Map<Class<? extends OperationRequest>, OperationFactory> for the factory created above
        MapBinder.newMapBinder(binder, operationRequestClassType, TypeLiteral.get(OperationFactory.class))
                .addBinding(requestClass).to(operationFactoryType);

        // add an entry to the Map<OperationType, Class<? extends OperationRequest>> for the typeId.
        // this allows OperationRequest.TypeIdResolver to lookup the Class for a given typeId (and vice versa)
        MapBinder.newMapBinder(binder, TypeLiteral.get(OperationType.class), operationRequestClassType)
                .addBinding(typeId).toInstance(requestClass);

        // add an entry to the Map<OperationType, Class<? extends Operation>> for the typeId.
        // this allows Operation.TypeIdResolver to lookup the Class for a given typeId (and vice versa)
        MapBinder.newMapBinder(binder, TypeLiteral.get(OperationType.class), operationClassType)
                .addBinding(typeId).toInstance(operationClass);
    }
}
