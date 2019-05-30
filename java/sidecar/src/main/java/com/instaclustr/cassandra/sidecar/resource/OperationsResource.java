package com.instaclustr.cassandra.sidecar.resource;

import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/operations")
@Produces(MediaType.APPLICATION_JSON)
public class OperationsResource {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public OperationsResource(final StorageServiceMBean storageServiceMBean) {
        this.storageServiceMBean = storageServiceMBean;
    }

    @POST
    @Path("/decommission")
    public void decommissionNode() {
        new Thread(() -> {
            try {
                storageServiceMBean.decommission();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Decommission Background Thread").start();
    }
}
