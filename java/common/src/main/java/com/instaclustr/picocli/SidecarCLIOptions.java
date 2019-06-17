package com.instaclustr.picocli;

import java.net.URI;

import com.instaclustr.picocli.typeconverter.HttpServerAddressTypeConverter;
import picocli.CommandLine;

public abstract class SidecarCLIOptions {

    private static final String DEFAULT_SIDECAR_HTTP_HOSTNAME = "0.0.0.0";

    private static final int DEFAULT_SIDECAR_HTTP_PORT = 4567;

    public static class CassandraSidecarHttpServerAddressTypeConverter extends HttpServerAddressTypeConverter {
        @Override
        protected String getDefaultHostname() {
            return DEFAULT_SIDECAR_HTTP_HOSTNAME;
        }

        @Override
        protected int defaultPort() {
            return DEFAULT_SIDECAR_HTTP_PORT;
        }
    }

    @CommandLine.Option(names = {"--http-service"},
            paramLabel = "[ADDRESS][:PORT]",
            defaultValue = ":" + DEFAULT_SIDECAR_HTTP_PORT,
            converter = CassandraSidecarHttpServerAddressTypeConverter.class,
            description = "Listen address (and optional port). " +
                    "ADDRESS may be a hostname, IPv4 dotted or decimal address, or IPv6 address. " +
                    "When ADDRESS is omitted, " + DEFAULT_SIDECAR_HTTP_HOSTNAME + " is used. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_SIDECAR_HTTP_PORT + " will be substituted if omitted. " +
                    "If ADDRESS is omitted but PORT is specified, PORT must be prefixed with a colon (':'), or PORT will be interpreted as a decimal IPv4 address. " +
                    "Defaults to '${DEFAULT-VALUE}'")
    public URI httpServiceAddress;
}
