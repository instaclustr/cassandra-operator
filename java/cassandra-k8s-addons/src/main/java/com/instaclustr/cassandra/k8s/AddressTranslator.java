package com.instaclustr.cassandra.k8s;

import java.net.InetAddress;
import java.util.List;

public interface AddressTranslator<T> {

    List<T> translate(List<InetAddress> addresses);

    String getHostname(T address);

    final class NoOpAddressTranslator implements AddressTranslator<InetAddress> {

        @Override
        public List<InetAddress> translate(final List<InetAddress> addresses) {
            return addresses;
        }

        @Override
        public String getHostname(final InetAddress address) {
            return address.getCanonicalHostName();
        }
    }
}
