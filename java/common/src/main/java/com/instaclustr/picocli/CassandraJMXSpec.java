package com.instaclustr.picocli;

import static com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter.DEFAULT_CASSANDRA_JMX_PORT;

import javax.management.remote.JMXServiceURL;

import java.nio.file.Path;

import com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter;
import picocli.CommandLine;

/**
 * Holds JMX connection information, used as mixin for application to achieve DRY.
 */
public class CassandraJMXSpec {

    @CommandLine.Option(names = "--jmx-service",
            paramLabel = "[ADDRESS][:PORT]|[JMX SERVICE URL]",
            defaultValue = ":" + DEFAULT_CASSANDRA_JMX_PORT,
            converter = CassandraJMXServiceURLTypeConverter.class,
            description = "Address (and optional port) of a Cassandra instance to connect to via JMX. " +
                    "ADDRESS may be a hostname, IPv4 dotted or decimal address, or IPv6 address. " +
                    "When ADDRESS is omitted, the loopback address is used. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_CASSANDRA_JMX_PORT + " will be substituted if omitted." +
                    "Defaults to '${DEFAULT-VALUE}'")
    public JMXServiceURL jmxServiceURL;

    @CommandLine.Option(names = "--jmx-user", paramLabel = "[STRING]", description = "User for JMX for Cassandra")
    public String jmxUser;

    @CommandLine.Option(names = "--jmx-password", paramLabel = "[STRING]", description = "Password for JMX for Cassandra")
    public String jmxPassword;

    @CommandLine.Option(names = "--jmx-truststore", paramLabel = "[PATH]", description = "Path to truststore file for Cassandra")
    public String trustStore;

    @CommandLine.Option(names = "--jmx-truststore-password", paramLabel = "[PATH]", description = "Password to truststore file for Cassandra")
    public String trustStorePassword;
}
