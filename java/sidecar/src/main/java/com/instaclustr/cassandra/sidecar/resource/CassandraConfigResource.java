package com.instaclustr.cassandra.sidecar.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/config")
@Produces("application/yaml")
public class CassandraConfigResource {

    @GET
    public Response getCassandraConfiguration() {
        try {
            final byte[] cassandraYaml = Files.readAllBytes(Paths.get("/var/lib/cassandra/cassandra-config.yaml"));

            return Response.ok(new String(cassandraYaml), "application/yaml").build();
        } catch (Exception ex) {
            return Response.serverError().build();
        }
    }
}
