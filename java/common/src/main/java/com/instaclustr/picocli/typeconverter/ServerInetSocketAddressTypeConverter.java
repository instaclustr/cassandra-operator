package com.instaclustr.picocli.typeconverter;

import java.net.InetAddress;

/**
 * A {@link InetSocketAddressTypeConverter} that has the wildcard address (0.0.0.0) as it's default address.
 *
 * Designed for CLI arguments that represent server socket listen addresses, with the default meaning "listen on all addresses".
 */
public abstract class ServerInetSocketAddressTypeConverter extends InetSocketAddressTypeConverter {
    @Override
    protected InetAddress defaultAddress() {
        return null; // null hostname == wildcard/any
    }
}
