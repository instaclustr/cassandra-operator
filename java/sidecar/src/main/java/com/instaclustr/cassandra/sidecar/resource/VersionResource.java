package com.instaclustr.cassandra.sidecar.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.instaclustr.cassandra.CassandraVersion;
import com.instaclustr.version.Version;

@Path("/version")
@Produces(APPLICATION_JSON)
public class VersionResource {

    private final Version version;
    private final Provider<CassandraVersion> cassandraVersion;

    @Inject
    public VersionResource(final Version version,
                           final Provider<CassandraVersion> cassandraVersion) {
        this.version = version;
        this.cassandraVersion = cassandraVersion;
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
        return Response.ok(cassandraVersion.get()).build();
    }
}
