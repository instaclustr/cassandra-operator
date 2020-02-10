package com.instaclustr.cassandra.k8s;

import java.net.InetAddress;

public interface AddressTranslator<T> {

    T[] translate(InetAddress[] addresses);

    String getHostname(T address);

    final class NoOpAddressTranslator implements AddressTranslator<InetAddress> {

        @Override
        public InetAddress[] translate(final InetAddress[] addresses) {
            return addresses;
        }

        @Override
        public String getHostname(final InetAddress address) {
            return address.getCanonicalHostName();
        }
    }
}
