package com.instaclustr.cassandra.sidecar;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.instaclustr.cassandra.sidecar.cassandra.CassandraModule;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.jersey.JerseyServerModule;
import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.concurrent.Callable;

import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;

@CommandLine.Command(name = "cassandra-sidecar",
        mixinStandardHelpOptions = true,
        description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
        versionProvider = SidecarJarManifestVersionProvider.class,
        sortOptions = false
)
public class Sidecar implements Callable<Void> {
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(names = "--jmx-service-url", paramLabel = "URL",
            defaultValue = "service:jmx:rmi:///jndi/rmi://localhost:7199/jmxrmi",
            converter = JMXServiceURLTypeConverter.class,
            description = "JMX service URL of the Cassandra instance to connect to. " +
                    "Defaults to '${DEFAULT-VALUE}'")
    private JMXServiceURL jmxServiceURL;

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {
        logCommandVersionInformation(commandSpec);

        final JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL);
        final MBeanServerConnection mBeanServerConnection = connector.getMBeanServerConnection();

        final Injector injector = Guice.createInjector(
                new ServiceManagerModule(),

                new CassandraModule(mBeanServerConnection),

                new SidecarModule(),
                new JerseyServerModule()
        );

        return injector.getInstance(Application.class).call();
    }
}
