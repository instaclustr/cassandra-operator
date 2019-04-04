package com.instaclustr.cassandra.sidecar.resource;

import com.instaclustr.cassandra.sidecar.model.Status;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public StatusResource(final StorageServiceMBean storageServiceMBean) {
        this.storageServiceMBean = storageServiceMBean;
    }

    @GET
    public Status getStatus() {
        final Status.OperationMode operationMode = Status.OperationMode.valueOf(storageServiceMBean.getOperationMode());

        return new Status(operationMode);
    }
}
