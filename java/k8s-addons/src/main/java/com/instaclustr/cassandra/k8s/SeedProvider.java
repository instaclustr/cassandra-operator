package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedProvider implements org.apache.cassandra.locator.SeedProvider {
    private static final Logger logger = LoggerFactory.getLogger(SeedProvider.class);

    private final String service;

    public SeedProvider(final Map<String, String> args) {
        service = args.get("service");
        if (service == null)
            throw new IllegalStateException(format("%s requires \"service\" argument.", SeedProvider.class));
    }

    @Override
    public List<InetAddress> getSeeds() {
        try {

            while (true) {
                // we are repeatedly executing "getAllByName" on host from respective service because
                // some InetAddresses, when asked for their getAllByName, are not returning hostname of a pod but only its ip address like
                // 10-20-30-40.{node-service}.svc.default.cluster.local so we can not decide if they should be seeds or not (seed is only a
                // node which hostname ends on "-0" - there will be 1 seed per rack and the first node in a rack always ends on "-0" as it is
                // the first node started in that stateful set)
                //
                // There seems to be some time until DNS records are truly propagated and resolved hostname
                // is indeed the hostname of a pod instead of an IP address.
                final ImmutableList<InetAddress> serviceAddresses = ImmutableList.copyOf(InetAddress.getAllByName(service));

                if (!serviceAddresses.stream().allMatch(this::allHostnamesResolved)) {
                    continue;
                }

                final List<InetAddress> seeds = new ArrayList<>();

                for (final InetAddress serviceAddress : serviceAddresses) {
                    try {
                        final InetAddress[] allByName = InetAddress.getAllByName(serviceAddress.getHostAddress());

                        for (final InetAddress byName : allByName) {
                            final String hostname = byName.getCanonicalHostName();

                            if (hostname.split("\\.")[0].endsWith("-0")) {
                                seeds.add(byName);
                            }
                        }
                    } catch (final Exception ex) {
                        logger.info(format("Unable to resolve hostname for %s.", serviceAddress), ex);
                    }
                }

                logger.info("Discovered {} seed nodes: {}", seeds.size(), seeds);

                return seeds;
            }
        } catch (final UnknownHostException e) {
            logger.warn("Unable to resolve k8s service {}.", service, e);
        }

        return ImmutableList.of();
    }

    private boolean allHostnamesResolved(final InetAddress inetAddress) {
        try {
            for (final InetAddress byName : InetAddress.getAllByName(inetAddress.getHostAddress())) {
                if (isIPAddress(byName.getCanonicalHostName().split("\\.")[0].replace("-", "."))) {
                    return false;
                }
            }

            return true;
        } catch (final Exception ex) {
            logger.info(format("Could not resolve hostname  for %s", inetAddress), ex);
        }

        return false;
    }

    private boolean isIPAddress(final String possibleIpAddress) {
        try {
            InetAddresses.forString(possibleIpAddress);
            return true;
        } catch (final IllegalArgumentException ex) {
            // intentionally returning false and not logging
            return false;
        }
    }
}
