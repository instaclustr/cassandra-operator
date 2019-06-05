package com.instaclustr.cassandra.sidecar.operation;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;

import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.operation.OperationState;
import com.instaclustr.cassandra.sidecar.model.operation.OperationType;
import com.instaclustr.cassandra.sidecar.model.result.OperationResult;

public interface OperationExecutor {

    <O extends Operation, R extends OperationResult> Future<R> submit(final OperationTask<O, R> operationTask);

    Collection<OperationTask> getOperations();

    Collection<OperationTask> getOperations(final OperationType operationType);

    Collection<OperationTask> getOperations(final OperationState operationState);

    Optional<OperationTask> getOperation(final UUID id);

    <O extends Operation, R extends OperationResult> boolean isAlreadyRunning(final OperationTask<O, R> operationToRun);
}
