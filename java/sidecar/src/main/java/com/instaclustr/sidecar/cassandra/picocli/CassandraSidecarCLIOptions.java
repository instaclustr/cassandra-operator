package com.instaclustr.sidecar.cassandra.picocli;

import javax.management.remote.JMXServiceURL;
import java.net.URI;

import com.instaclustr.picocli.typeconverter.HttpServerAddressTypeConverter;
import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import picocli.CommandLine.Option;

public class CassandraSidecarCLIOptions {

    private static final String DEFAULT_SIDECAR_HTTP_HOSTNAME = "0.0.0.0";

    private static final int DEFAULT_SIDECAR_HTTP_PORT = 4567;

    private static final String DEFAULT_CASSANDRA_JMX_HOSTNAME = "127.0.0.1";

    private static final int DEFAULT_CASSANDRA_JMX_PORT = 7199;

    static class CassandraSidecarHttpServerAddressTypeConverter extends HttpServerAddressTypeConverter {
        @Override
        protected String getDefaultHostname() {
            return DEFAULT_SIDECAR_HTTP_HOSTNAME;
        }

        @Override
        protected int defaultPort() {
            return DEFAULT_SIDECAR_HTTP_PORT;
        }
    }

    static class CassandraSidecarJMXServiceURLTypeConverter extends JMXServiceURLTypeConverter {
        @Override
        protected String getDefaultHostname() {
            return DEFAULT_CASSANDRA_JMX_HOSTNAME;
        }

        @Override
        public int defaultPort() {
            return DEFAULT_CASSANDRA_JMX_PORT;
        }
    }

    @Option(names = {"--http-service"},
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

    @Option(names = "--jmx-service",
            paramLabel = "[ADDRESS][:PORT]",
            defaultValue = ":" + DEFAULT_CASSANDRA_JMX_PORT,
            converter = CassandraSidecarJMXServiceURLTypeConverter.class,
            description = "Address and port of Cassandra instance to connect to via JMX. " +
                    "ADDRESS may be a hostname, IPv4 dotted or decimal address, or IPv6 address. " +
                    "When ADDRESS is omitted, " + DEFAULT_CASSANDRA_JMX_HOSTNAME + " is used. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_CASSANDRA_JMX_PORT + " will be substituted if omitted." +
                    "Defaults to '${DEFAULT-VALUE}'")
    public JMXServiceURL jmxServiceURL;
}
