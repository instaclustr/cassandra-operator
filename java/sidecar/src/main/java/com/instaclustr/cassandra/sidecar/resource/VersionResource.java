package com.instaclustr.cassandra.sidecar.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.instaclustr.version.Version;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

@Path("/version")
@Produces(APPLICATION_JSON)
public class VersionResource {

    private final Version version;
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public VersionResource(final Version version,
                           final StorageServiceMBean storageServiceMBean) {
        this.version = version;
        this.storageServiceMBean = storageServiceMBean;
    }

    @GET
    public Response getVersion() {
        return getSidecarVersion();
    }

    @GET
    @Path("sidecar")
    public Response getSidecarVersion() {
        return Response.ok(version).build();
    }

    @GET
    @Path("cassandra")
    public Response getCassandraVersion() {
        try {
            return Response.ok(new Version(storageServiceMBean.getReleaseVersion())).build();
        } catch (final Exception ex) {
            return Response.serverError().build();
        }
    }
}
