package com.instaclustr.cassandra.sidecar.picocli;

import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;

import picocli.CommandLine;

// This converter is not in commons because there is not UriBuilder from javax.ws.rs.core
public class SidecarURIConverter implements CommandLine.ITypeConverter<URI> {

    @Override
    public URI convert(String value) throws Exception {

        String protocol = "http://";
        String port = ":4567/";
        String hostname = InetAddress.getLocalHost().getHostName();

        if (hostname.equals("127.0.0.1")) {
            hostname = "localhost";
        }

        try {
            if (value == null) {
                return UriBuilder.fromUri(protocol + hostname + port).build();
            } else {
                return UriBuilder.fromUri(value).build();
            }
        } catch (Exception ex) {
            throw new CommandLine.TypeConversionException("Invalid HTTP service URI (" + ex.getLocalizedMessage() + ")");
        }
    }
}
