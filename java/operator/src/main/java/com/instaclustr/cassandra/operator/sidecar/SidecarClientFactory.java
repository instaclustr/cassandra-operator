package com.instaclustr.cassandra.operator.sidecar;

import com.google.common.net.InetAddresses;
import io.kubernetes.client.models.V1Pod;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class SidecarClientFactory {
    private final Client client;

    @Inject
    public SidecarClientFactory() {
        client = ClientBuilder.newClient();
    }

    public SidecarClient clientForAddress(final InetAddress address) {
        final InetSocketAddress addressAndPort = new InetSocketAddress(address, 4567);

        return new SidecarClient(addressAndPort, client);
    }

    public SidecarClient clientForPod(final V1Pod pod) {
        return clientForAddress(InetAddresses.forString(pod.getStatus().getPodIP()));
    }
}
