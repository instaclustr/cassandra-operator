package com.instaclustr.cassandra.sidecar.resource;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("/repairs")
public class RepairsResource {

    @GET
    public String listRepairs() {
        return "foo";
    }

    @POST
    public String createRepair() {
        return "bar";
    }
}
