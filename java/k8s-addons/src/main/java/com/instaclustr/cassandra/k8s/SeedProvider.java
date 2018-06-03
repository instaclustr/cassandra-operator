package com.instaclustr.cassandra.k8s;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class SeedProvider implements org.apache.cassandra.locator.SeedProvider {
    private static final Logger logger = LoggerFactory.getLogger(SeedProvider.class);

    private final String service;

    public SeedProvider(final Map<String, String> args) {
        service = args.get("service");
        if (service == null)
            throw new IllegalStateException(String.format("%s requires \"service\" argument.", SeedProvider.class));
    }

    @Override
    public List<InetAddress> getSeeds() {
        try {
            final ImmutableList<InetAddress> seedAddresses = ImmutableList.copyOf(InetAddress.getAllByName(service));

            logger.info("Discovered {} seed nodes: {}", seedAddresses.size(), seedAddresses);

            return seedAddresses;

        } catch (final UnknownHostException e) {
            logger.warn("Unable to resolve k8s service {}.", service, e);
        }

        return ImmutableList.of();
    }
}
