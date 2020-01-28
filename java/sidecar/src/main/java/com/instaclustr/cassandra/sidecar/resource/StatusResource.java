package com.instaclustr.cassandra.sidecar.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.instaclustr.cassandra.sidecar.service.CassandraStatusService;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status;

@Path("/status")
@Produces(APPLICATION_JSON)
public class StatusResource {

    private final CassandraStatusService statusService;

    @Inject
    public StatusResource(final CassandraStatusService statusService) {
        this.statusService = statusService;
    }

    @GET
    public Response getStatus() {

        final Status status = statusService.getStatus();

        if (status.getException() != null) {
            return Response.serverError().entity(status).build();
        }

        return Response.ok(status).build();
    }

}
