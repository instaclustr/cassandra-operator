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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

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
    public Collection<Operation> getOperations() {
        return operationsService.operations().values();
    }

    @GET
    @Path("{id}")
    public Operation getOperationById(@NotNull @PathParam("id") final UUID id) {
        final Operation operation = operationsService.operations().get(id);

        if (operation == null) {
            throw new NotFoundException();
        }

        return operation;
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
