package com.instaclustr.picocli.typeconverter;

import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import com.google.common.base.Splitter;
import picocli.CommandLine;

public abstract class URITypeConverter implements CommandLine.ITypeConverter<URI> {

    @Override
    public URI convert(final String value) throws Exception {
        final List<String> addressParts = Splitter.on(':').limit(2).splitToList(value);

        String hostname = addressParts.get(0).trim();
        hostname = (hostname.length() == 0 ? null : hostname); // and empty hostname == wildcard/any

        if (hostname == null) {
            hostname = InetAddress.getLocalHost().getHostName();
            if (hostname.equals("127.0.0.1")) {
                hostname = "localhost";
            }
        }

        int port = defaultPort();
        if (addressParts.size() == 2) {
            try {
                port = Integer.parseInt(addressParts.get(1).trim());

            } catch (final NumberFormatException e) {
                throw new CommandLine.TypeConversionException("Specified port is not a valid number");
            }
        }

        String protocol = getProtocol();

        if (protocol == null) {
            throw new CommandLine.TypeConversionException("There was not any protocol specified");
        }

        String path = getPath();

        if (path == null) {
            path = "";
        }

        return UriBuilder.fromUri(String.format("%s://%s:%s/%s", protocol, hostname, port, path)).build();
    }

    protected abstract int defaultPort();

    protected abstract String getProtocol();

    protected String getPath() {
        return "";
    }
}
