package com.instaclustr.cassandra.sidecar.resource;

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
            status.setNodeState(Status.NodeState.valueOf(storageServiceMBean.getOperationMode()));
        } catch (Exception ex) {
            status.setException(ex);
        }

        if (status.getException() != null) {
            return Response.serverError().entity(status).build();
        }

        return Response.ok(status).build();
    }

    static class Status {

        public enum NodeState {
            STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
        }

        private NodeState nodeState;

        private Exception exception;

        public NodeState getNodeState() {
            return nodeState;
        }

        public void setNodeState(NodeState nodeState) {
            this.nodeState = nodeState;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }
}
