package com.instaclustr.picocli.typeconverter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine;

/**
 * A {@link picocli.CommandLine.ITypeConverter} for {@link InetSocketAddress}es.
 *
 * The value to convert should follow the format "[ADDRESS][:PORT]", where ADDRESS can be a DNS hostname,
 * an IPv4 dotted or decimal address literal, or an IPv6 address literal (enclosed in square brackets '[]').
 * PORT must be a valid port number.
 *
 * If ADDRESS is omitted but PORT is specified, PORT must be prefixed with a colon (':'),
 * or PORT will be interpreted as an ADDRESS (a decimal IPv4 address).
 *
 * If ADDRESS is omitted, defaultAddress() will be called to provide the hostname (by default, the loopback address)
 * If PORT is omitted, defaultPort() will be called to provide the port.
 */
public abstract class InetSocketAddressTypeConverter implements CommandLine.ITypeConverter<InetSocketAddress> {
    // This regex is intentionally permissive so that fine grained checks can be performed to give more precise error messages
    // i.e, matcher.matches() should always succeed, but the resulting groups may be null or empty.
    // It matches the empty string (resulting in address & port group = null),
    // ports can contain non-numeric values, etc.
    private static final Pattern VALUE_PATTERN = Pattern.compile("^\\s*(?<address>\\[[^]]+?]|[^:]+?)?\\s*(:\\s*(?<port>.*))?");

    @Override
    public InetSocketAddress convert(final String value) {
        final Matcher matcher = VALUE_PATTERN.matcher(value);

        if (!matcher.matches()) {
            throw new CommandLine.TypeConversionException("Specified address is not valid");
        }

        final String address = matcher.group("address");
        final String portString = matcher.group("port");

        int port = defaultPort();
        if (portString != null) {
            try {
                port = Integer.parseInt(portString.trim());

            } catch (final NumberFormatException e) {
                throw new CommandLine.TypeConversionException(String.format("Specified port \"%s\" is not a valid number", portString));
            }
        }

        final InetSocketAddress socketAddress;
        try {
            socketAddress = address == null ?
                    new InetSocketAddress(defaultAddress(), port) :
                    new InetSocketAddress(address, port);

        } catch (final IllegalArgumentException e) {
            // invalid port, etc...
            throw new CommandLine.TypeConversionException(e.getLocalizedMessage());
        }

        if (socketAddress.isUnresolved()) {
            throw new CommandLine.TypeConversionException(String.format("Could not resolve host \"%s\"", socketAddress.getHostString()));
        }

        return socketAddress;
    }

    protected InetAddress defaultAddress() {
        return InetAddress.getLoopbackAddress();
    }

    protected abstract int defaultPort();
}
