package com.instaclustr.cassandra.sidecar;

import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXServiceURL;
import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.Callable;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.instaclustr.cassandra.sidecar.cassandra.CassandraModule;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import com.instaclustr.cassandra.sidecar.picocli.SidecarURIConverter;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.jersey.JerseyServerModule;
import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

@CommandLine.Command(name = "cassandra-sidecar",
        mixinStandardHelpOptions = true,
        description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
        versionProvider = SidecarJarManifestVersionProvider.class,
        sortOptions = false
)
public class Sidecar implements Callable<Void> {

    private static final Logger logger = LoggerFactory.getLogger(Sidecar.class);

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(
            names = "--jmx-service-url",
            paramLabel = "URL",
            defaultValue = "service:jmx:rmi:///jndi/rmi://localhost:7199/jmxrmi",
            converter = JMXServiceURLTypeConverter.class,
            description = "JMX service URL of the Cassandra instance to connect to. Defaults to '${DEFAULT-VALUE}'")
    private JMXServiceURL jmxServiceURL;

    @CommandLine.Option(
            names = {"--http-service-uri"},
            paramLabel = "URI",
            defaultValue = "http://127.0.0.1:4567/",
            converter = SidecarURIConverter.class,
            description = "URI where HTTP server of Sidecar will be running. Defaults to ${DEFAULT_VALUE}")
    private URI httpServiceURI;

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {
        logCommandVersionInformation(commandSpec);

        final MBeanServerConnection mBeanServerConnection = new JMXHelper().getMBeanServerConnection(jmxServiceURL);

        final Injector injector = Guice.createInjector(
                new ServiceManagerModule(),

                new CassandraModule(mBeanServerConnection),

                new SidecarModule(),
                new JerseyServerModule(httpServiceURI)
        );

        return injector.getInstance(Application.class).call();
    }
}
