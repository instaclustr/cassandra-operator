package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.cassandra.locator.InetAddressAndPort;

public class SeedProvider implements org.apache.cassandra.locator.SeedProvider {

    private final String service;

    public SeedProvider(final Map<String, String> args) {
        service = args.get("service");
        if (service == null) {
            throw new IllegalStateException(format("%s requires \"service\" argument.", SeedProvider.class));
        }
    }

    @Override
    public List<InetAddressAndPort> getSeeds() {
        try {
            return new SeedsResolver<>(service, new InetAddressAndPortAddressTranslator()).resolve();
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to resolve any seeds!", ex);
        }
    }

    public static final class InetAddressAndPortAddressTranslator implements AddressTranslator<InetAddressAndPort> {

        @Override
        public List<InetAddressAndPort> translate(final List<InetAddress> addresses) {
            return addresses.stream().map(InetAddressAndPort::getByAddress).collect(Collectors.toList());
        }

        @Override
        public String getHostname(final InetAddressAndPort address) {
            return address.address.getCanonicalHostName();
        }
    }
}
