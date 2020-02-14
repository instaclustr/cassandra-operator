package com.instaclustr.cassandra.k8s;

import static java.lang.String.format;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.net.InetAddresses;
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
        return new SeedsResolver<InetAddressAndPort>(service, new InetAddressAndPortAddressTranslator()) {
            @Override
            public boolean isIPAddress(final String possibleIpAddress) {
                try {
                    InetAddresses.forString(possibleIpAddress);
                    return true;
                } catch (Exception e) {
                    // intentionally returning false
                    return false;
                }
            }
        }.resolve();
    }

    public static final class InetAddressAndPortAddressTranslator implements AddressTranslator<InetAddressAndPort> {

        @Override
        public InetAddressAndPort[] translate(final InetAddress[] addresses) {
            return Arrays.stream(addresses).map(InetAddressAndPort::getByAddress).toArray(InetAddressAndPort[]::new);
        }

        @Override
        public String getHostname(final InetAddressAndPort address) {
            return address.address.getCanonicalHostName();
        }
    }
}
