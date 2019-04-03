package com.instaclustr.cassandra.operator.sidecar;

import com.instaclustr.cassandra.sidecar.model.Status;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;

public class SidecarClient {
    private final WebTarget statusTarget;
    private final WebTarget operationsTarget;

    public SidecarClient(final InetSocketAddress address, final Client client) {
        final UriBuilder uriBuilder = UriBuilder.fromUri("http:///")
                .host(address.getHostString())
                .port(address.getPort());

        final WebTarget rootTarget = client.target(uriBuilder);

        statusTarget = rootTarget.path("/status");
        operationsTarget = rootTarget.path("/operations");
    }

    public Future<Void> decommission() {
        return operationsTarget.path("/decommission").request().async().post(null, Void.class);
    }

    public Future<Status> status() {
        return statusTarget.request().async().get(Status.class);
    }


}
