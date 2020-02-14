package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import com.google.common.net.InetAddresses;
import com.instaclustr.cassandra.k8s.AddressTranslator.NoOpAddressTranslator;

public class SeedProvider implements org.apache.cassandra.locator.SeedProvider {

    private final String service;

    public SeedProvider(final Map<String, String> args) {
        service = args.get("service");
        if (service == null) {
            throw new IllegalStateException(format("%s requires \"service\" argument.", SeedProvider.class));
        }
    }

    @Override
    public List<InetAddress> getSeeds() {
        return new SeedsResolver<InetAddress>(service, new NoOpAddressTranslator()) {
            @Override
            public boolean isIPAddress(final String possibleIpAddress) {
                try {
                    InetAddresses.forString(possibleIpAddress);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }.resolve();
    }
}
