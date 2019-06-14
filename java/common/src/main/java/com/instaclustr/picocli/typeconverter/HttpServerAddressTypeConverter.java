package com.instaclustr.picocli.typeconverter;

import java.net.URI;

import picocli.CommandLine;

public abstract class HttpServerAddressTypeConverter extends HostnamePortConverter implements CommandLine.ITypeConverter<URI> {

    @Override
    public URI convert(final String value) throws Exception {

        final HostnameAndPort hostnameAndPort = getHostnameAndPort(value);

        try {
            return URI.create(String.format("http://%s:%s/", hostnameAndPort.hostname, hostnameAndPort.port));
        } catch (Exception ex) {
            throw new CommandLine.TypeConversionException("Invalid resolution of HTTP server address (" + ex.getLocalizedMessage() + ")");
        }
    }

    protected abstract String getDefaultHostname();

    protected abstract int defaultPort();
}
