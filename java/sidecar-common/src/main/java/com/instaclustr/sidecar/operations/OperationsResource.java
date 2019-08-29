package com.instaclustr.sidecar.operations;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Collections2;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationRequest;
import com.instaclustr.operations.OperationsService;

/**
 * Common operation JAX-RS resource exposing operation endpoints.
 * This resource is automatically used / registered in any Sidecar just by mere presence on the classpath.
 */
@Path("/operations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class OperationsResource {
    private final OperationsService operationsService;

    @Inject
    public OperationsResource(final OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GET
    public Collection<Operation> getOperations(@QueryParam("type") final Set<Class<? extends Operation>> operationTypesFilter,
                                               @QueryParam("state") final Set<Operation.State> statesFilter) {
        Collection<Operation> operations = operationsService.operations().values();

        if (!operationTypesFilter.isEmpty()) {
            operations = Collections2.filter(operations, input -> {
                if (input == null) {
                    return false;
                }

                return operationTypesFilter.contains(input.getClass());
            });
        }

        if (!statesFilter.isEmpty()) {
            operations = Collections2.filter(operations, input -> {
                if (input == null) {
                    return false;
                }

                return statesFilter.contains(input.state);
            });
        }

        return operations;
    }

    @GET
    @Path("{id}")
    public Operation getOperationById(@NotNull @PathParam("id") final UUID id) {
        return operationsService.operation(id).orElseThrow(NotFoundException::new);
    }

    @POST
    public Response createNewOperation(@Valid final OperationRequest request) {
        final Operation operation = operationsService.submitOperationRequest(request);

        final URI operationLocation = UriBuilder.fromResource(OperationsResource.class)
                                                .path(OperationsResource.class, "getOperationById")
                                                .build(operation.id);

        return Response.created(operationLocation).entity(operation).build();
    }

}
