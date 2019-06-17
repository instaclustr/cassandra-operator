package com.instaclustr.picocli.typeconverter;

import java.util.List;

import com.google.common.base.Splitter;
import picocli.CommandLine;

public abstract class HostnamePortConverter {

    public HostnameAndPort getHostnameAndPort(String value) throws Exception {

        final List<String> addressParts = Splitter.on(':').limit(2).splitToList(value);

        String hostname = addressParts.get(0).trim();
        hostname = (hostname.length() == 0 ? null : hostname);

        if (hostname == null) {
            hostname = getDefaultHostname();
        }

        int port = defaultPort();

        if (addressParts.size() == 2) {

            final String trimmedPort = addressParts.get(1).trim();

            try {
                port = Integer.parseInt(trimmedPort);

            } catch (final NumberFormatException e) {
                throw new CommandLine.TypeConversionException(String.format("Specified port '%s'is not a valid number", trimmedPort));
            }
        }

        return new HostnameAndPort(hostname, port);
    }

    protected abstract String getDefaultHostname();

    protected abstract int defaultPort();

    public final class HostnameAndPort {
        public final String hostname;
        public final int port;

        public HostnameAndPort(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }
    }
}
