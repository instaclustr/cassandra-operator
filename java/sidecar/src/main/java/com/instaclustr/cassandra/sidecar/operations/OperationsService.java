package com.instaclustr.cassandra.sidecar.operations;

import com.google.common.util.concurrent.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OperationsService extends AbstractIdleService {
    private final ListeningExecutorService executorService;

    private final Map<Class<? extends OperationRequest>, OperationFactory> operationFactoriesByRequestType;

    private final Map<UUID, Operation> operations = new HashMap<>();

    @Inject
    public OperationsService(final Map<Class<? extends OperationRequest>, OperationFactory> operationFactoriesByRequestType) {
        this.operationFactoriesByRequestType = operationFactoriesByRequestType;

        // TODO: custom executor implementation that allows for concurrent operations of different types
        this.executorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    }

    @Override
    protected void startUp() throws Exception {}

    @Override
    protected void shutDown() throws Exception {
        MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.MINUTES);
    }


    public void submitOperation(final Operation operation) {
        operations.put(operation.id, operation);

        final ListenableFuture<?> operationFuture = executorService.submit(operation);

        // TODO: somehow "expire" completed (successful or not) operations from the map
        // perhaps the map should be a cache of some sort
        Futures.addCallback(operationFuture, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable final Object result) {}

            @Override
            public void onFailure(final Throwable t) {}
        }, MoreExecutors.newDirectExecutorService());
    }

    public Operation submitOperationRequest(final OperationRequest request) {
        final OperationFactory operationFactory = operationFactoriesByRequestType.get(request.getClass());

        final Operation operation = operationFactory.createOperation(request);

        submitOperation(operation);

        return operation;
    }

    public Map<UUID, Operation> operations() {
        return Collections.unmodifiableMap(operations);
    }
}
