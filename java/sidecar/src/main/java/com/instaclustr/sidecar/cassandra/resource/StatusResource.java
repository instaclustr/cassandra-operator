package com.instaclustr.sidecar.cassandra.resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    static class Status {

        public enum OperationMode {
            STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
        }

        private OperationMode operationMode;

        private Exception exception;

        public OperationMode getOperationMode() {
            return operationMode;
        }

        public void setOperationMode(OperationMode operationMode) {
            this.operationMode = operationMode;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }
}
