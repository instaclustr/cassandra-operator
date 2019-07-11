package com.instaclustr.picocli.typeconverter;

import javax.management.remote.JMXServiceURL;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;

import picocli.CommandLine;

/**
 * A {@link picocli.CommandLine.ITypeConverter} for {@link JMXServiceURL}s.
 *
 * The value to convert should either resemble a RFC 2609 JMX Abstract Service URL (service:jmx:...//)
 * (see {@link JMXServiceURL} for details), in which case it will be converted to a {@link JMXServiceURL} directly,
 * or resemble a valid value for a {@link InetSocketAddressTypeConverter} (e.g, [ADDRESS][:PORT]), in which case it will
 * be converted to a {@link JMXServiceURL} using RMI as the protocol and the {@link InetSocketAddress} as the address of the
 * RMI Registry.
 */
public abstract class JMXServiceURLTypeConverter implements CommandLine.ITypeConverter<JMXServiceURL> {
    private class JMXInetSocketAddressTypeConverter extends InetSocketAddressTypeConverter {
        @Override
        protected InetAddress defaultAddress() {
            return JMXServiceURLTypeConverter.this.defaultAddress();
        }

        @Override
        protected int defaultPort() {
            return JMXServiceURLTypeConverter.this.defaultPort();
        }
    }

    @Override
    public JMXServiceURL convert(final String value) {
        try {
            if (value.startsWith("service:jmx:")) {
                // tastes like a JMX service URL
                return new JMXServiceURL(value);

            } else {
                // convert InetSocketAddress to JMXServiceURL for the common case
                final InetSocketAddress socketAddress = new JMXInetSocketAddressTypeConverter().convert(value);

                return new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", socketAddress.getHostString(), socketAddress.getPort()));
            }

        } catch (final MalformedURLException e) {
            throw new CommandLine.TypeConversionException("Invalid JMX service URL (" + e.getLocalizedMessage() + ")");
        }
    }

    protected InetAddress defaultAddress() {
        return InetAddress.getLoopbackAddress();
    }

    protected abstract int defaultPort();
}
