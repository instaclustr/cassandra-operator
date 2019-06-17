package com.instaclustr.sidecar.cassandra.picocli;

import javax.management.remote.JMXServiceURL;

import com.instaclustr.picocli.SidecarCLIOptions;
import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import picocli.CommandLine.Option;

public class CassandraSidecarCLIOptions extends SidecarCLIOptions {

    private static final String DEFAULT_CASSANDRA_JMX_HOSTNAME = "127.0.0.1";

    private static final int DEFAULT_CASSANDRA_JMX_PORT = 7199;

    public static class CassandraSidecarJMXServiceURLTypeConverter extends JMXServiceURLTypeConverter {
        @Override
        protected String getDefaultHostname() {
            return DEFAULT_CASSANDRA_JMX_HOSTNAME;
        }

        @Override
        public int defaultPort() {
            return DEFAULT_CASSANDRA_JMX_PORT;
        }
    }

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
