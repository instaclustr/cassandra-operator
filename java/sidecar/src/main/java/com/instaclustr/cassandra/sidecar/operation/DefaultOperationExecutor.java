package com.instaclustr.cassandra.sidecar.operation;

import static java.util.Collections.unmodifiableCollection;
import static java.util.stream.Collectors.toList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.operation.OperationState;
import com.instaclustr.cassandra.sidecar.model.operation.OperationType;
import com.instaclustr.cassandra.sidecar.model.result.OperationResult;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class DefaultOperationExecutor implements OperationExecutor {

    private final StorageServiceMBean storageServiceMBean;

    private final ExecutorService executorService;

    private final List<OperationTask> operations = new ArrayList<>();

    @Inject
    public DefaultOperationExecutor(final StorageServiceMBean storageServiceMBean, final ExecutorService executorService) {
        this.storageServiceMBean = storageServiceMBean;
        this.executorService = executorService;
    }

    @Override
    public <T extends Operation, R extends OperationResult> Future<R> submit(final OperationTask<T, R> operationTask) {

        operationTask.setStorageServiceMBean(storageServiceMBean);

        operationTask.getOperationResult().setOperationState(OperationState.SUBMITTED);

        operations.add(operationTask);

        return executorService.submit(operationTask);
    }

    @Override
    public Collection<OperationTask> getOperations() {
        return unmodifiableCollection(operations);
    }

    @Override
    public Collection<OperationTask> getOperations(final OperationType operationType) {
        return unmodifiableCollection(operations.stream().filter(op -> op.getOperation().getType() == operationType).collect(toList()));
    }

    @Override
    public Collection<OperationTask> getOperations(final OperationState operationState) {
        return unmodifiableCollection(operations.stream().filter(op -> op.getOperationResult().getOperationState() == operationState).collect(toList()));
    }

    @Override
    public Optional<OperationTask> getOperation(final UUID id) {
        return operations.stream().filter(op -> op.getOperation().getId().equals(id)).findFirst();
    }

    @Override
    public <O extends Operation, R extends OperationResult> boolean isAlreadyRunning(OperationTask<O, R> operationToRun) {
        return getOperations().stream().anyMatch(op -> op.getOperation().getType() == operationToRun.getOperation().getType());
    }
}
