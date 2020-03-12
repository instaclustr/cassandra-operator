package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

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
        try {
            return new SeedsResolver<>(service, new NoOpAddressTranslator()).resolve();
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to resolve any seeds!", ex);
        }
    }
}
