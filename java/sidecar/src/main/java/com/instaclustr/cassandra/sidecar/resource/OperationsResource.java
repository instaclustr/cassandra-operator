package com.instaclustr.cassandra.sidecar.resource;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;
import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.result.OperationResult;
import com.instaclustr.cassandra.sidecar.operation.OperationExecutor;
import com.instaclustr.cassandra.sidecar.operation.OperationTask;
import com.instaclustr.cassandra.sidecar.operation.task.TaskFactory;

@Path(OperationsResource.ENDPOINT)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperationsResource {

    public static final String ENDPOINT = "/operations-old";

    private final OperationExecutor operationExecutor;

    private final TaskFactory taskFactory;

    @Inject
    public OperationsResource(final OperationExecutor operationExecutor, final TaskFactory taskFactory) {
        this.operationExecutor = operationExecutor;
        this.taskFactory = taskFactory;
    }

    @GET
    public Response getOperations() {
        return Response.ok(operationExecutor.getOperations().stream().map(OperationTask::getOperationResult)).build();
    }

    @GET
    @Path("{id}")
    public Response getOperation(final @PathParam("id") UUID operationId) {

        final Optional<OperationTask> operationTask = operationExecutor.getOperation(operationId);

        if (operationTask.isPresent()) {
            return Response.ok(operationTask.get().getOperationResult()).build();
        } else {
            return Response.status(NOT_FOUND).build();
        }
    }

    @POST
    public Response executeOperation(final Operation operation) {

        if (operation.getId() == null) {
            operation.setId(UUID.randomUUID());
        }

        OperationTask<? extends Operation, ? extends OperationResult> operationTask;

        Class<?> operationClass = operation.getClass();

        if (operationClass == DecommissionOperation.class) {
            operationTask = taskFactory.createDecommissionTask((DecommissionOperation) operation);
        } else if (operationClass == BackupOperation.class) {
            operationTask = taskFactory.createBackupTask((BackupOperation) operation);
        } else {
            return Response.status(BAD_REQUEST.getStatusCode(), String.format("Operation %s is not supported yet.", operation.getType())).build();
        }

        if (operationExecutor.isAlreadyRunning(operationTask)) {
            return Response.status(BAD_REQUEST.getStatusCode(),
                                   String.format("Can not submit operation of type %s. Same operation is already running.", operationTask.getOperation().getType())).build();
        }

        operationExecutor.submit(operationTask);

        return Response.created(URI.create(ENDPOINT + "/" + operationTask.getOperation().getId())).build();
    }
}
