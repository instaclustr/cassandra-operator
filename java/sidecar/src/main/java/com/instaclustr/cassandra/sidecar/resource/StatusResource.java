package com.instaclustr.cassandra.sidecar.resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.instaclustr.cassandra.sidecar.model.Status;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public StatusResource(final StorageServiceMBean storageServiceMBean) {
        this.storageServiceMBean = storageServiceMBean;
    }

    @GET
    public Response getStatus() {

        final Status status = new Status();

        try {
            status.setOperationMode(Status.OperationMode.valueOf(storageServiceMBean.getOperationMode()));
        } catch (Exception ex) {
            status.setException(ex);
        }

        if (status.getException() != null) {
            return Response.serverError().entity(status).build();
        }

        return Response.ok(status).build();
    }
}
