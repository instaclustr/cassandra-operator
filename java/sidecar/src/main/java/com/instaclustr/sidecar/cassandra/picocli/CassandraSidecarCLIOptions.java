package com.instaclustr.sidecar.cassandra.picocli;

import javax.management.remote.JMXServiceURL;
import java.net.URI;

import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import com.instaclustr.picocli.typeconverter.URITypeConverter;
import picocli.CommandLine.Option;

public class CassandraSidecarCLIOptions {

    private static final String WILDCARD_ADDRESS = "0.0.0.0";

    private static final int DEFAULT_PORT = 4567;

    static class HttpServerUriTypeConverter extends URITypeConverter {
        @Override
        protected int defaultPort() {
            return DEFAULT_PORT;
        }

        @Override
        protected String getProtocol() {
            return "http";
        }
    }

    @Option(names = {"--http-service"},
            paramLabel = "[ADDRESS][:PORT]",
            defaultValue = ":" + DEFAULT_PORT,
            converter = HttpServerUriTypeConverter.class,
            description = "Listen address (and optional port). " +
                    "ADDRESS may be a hostname, IPv4 dotted or decimal address, or IPv6 address. " +
                    "When ADDRESS is omitted, " + WILDCARD_ADDRESS + " (wildcard) is substituted. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_PORT + " will be substituted if omitted. " +
                    "If ADDRESS is omitted but PORT is specified, PORT must be prefixed with a colon (':'), or PORT will be interpreted as a decimal IPv4 address. " +
                    "This option may be specified more than once to listen on multiple addresses. " +
                    "Defaults to '${DEFAULT-VALUE}'")
    public URI httpServiceAddress;

    @Option(names = "--jmx-service-url",
            paramLabel = "URL",
            defaultValue = "service:jmx:rmi:///jndi/rmi://localhost:7199/jmxrmi",
            converter = JMXServiceURLTypeConverter.class,
            description = "JMX service URL of the Cassandra instance to connect to. Defaults to '${DEFAULT-VALUE}'")
    public JMXServiceURL jmxServiceURL;
}
