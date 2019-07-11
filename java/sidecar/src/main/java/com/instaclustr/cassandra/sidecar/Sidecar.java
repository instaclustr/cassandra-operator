package com.instaclustr.cassandra.sidecar;

import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;

import javax.management.remote.JMXServiceURL;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.instaclustr.cassandra.sidecar.cassandra.CassandraModule;
import com.instaclustr.cassandra.sidecar.operations.backup.BackupsModule;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupsModule;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissioningModule;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildModule;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubModule;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesModule;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.picocli.typeconverter.JMXServiceURLTypeConverter;
import com.instaclustr.picocli.typeconverter.ServerInetSocketAddressTypeConverter;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.sidecar.operations.OperationsModule;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

@Command(name = "cassandra-sidecar",
        mixinStandardHelpOptions = true,
        description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
        versionProvider = SidecarJarManifestVersionProvider.class,
        sortOptions = false
)
public final class Sidecar implements Callable<Void> {

    private static final int DEFAULT_SIDECAR_HTTP_PORT = 4567;
    private static final int DEFAULT_CASSANDRA_JMX_PORT = 7199;

    public static final class HttpServerInetSocketAddressTypeConverter extends ServerInetSocketAddressTypeConverter {
        @Override
        protected int defaultPort() {
            return DEFAULT_SIDECAR_HTTP_PORT;
        }
    }

    @CommandLine.Option(names = {"-l", "--listen"},
            paramLabel = "[ADDRESS][:PORT]",
            defaultValue = ":" + DEFAULT_SIDECAR_HTTP_PORT,
            converter = HttpServerInetSocketAddressTypeConverter.class,
            description = "Listen address (and optional port) for the API endpoint HTTP server. " +
                    "ADDRESS must be a resolvable hostname, IPv4 dotted or decimal address, or IPv6 address (enclosed in square brackets). " +
                    "When ADDRESS is omitted, the server will bind on all interfaces. " +
                    "PORT, when specified, must be a valid port number. The default port " + DEFAULT_SIDECAR_HTTP_PORT + " will be substituted if omitted. " +
                    "If ADDRESS is omitted but PORT is specified, PORT must be prefixed with a colon (':'), or PORT will be interpreted as a decimal IPv4 address. " +
                    "Defaults to '${DEFAULT-VALUE}'")
    public InetSocketAddress httpServerAddress;

    public static final class CassandraJMXServiceURLTypeConverter extends JMXServiceURLTypeConverter {
        @Override
        protected int defaultPort() {
            return DEFAULT_CASSANDRA_JMX_PORT;
        }
    }

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

    @Spec
    private CommandLine.Model.CommandSpec commandSpec;


    public static void main(final String[] args) {

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {

        logCommandVersionInformation(commandSpec);

        final Injector injector = Guice.createInjector(
                Stage.PRODUCTION, // production binds singletons as eager by default

                new ServiceManagerModule(),

                new CassandraModule(jmxServiceURL),
                new JerseyHttpServerModule(httpServerAddress),

                new OperationsModule(3600), // TODO - make this configurable from the cmd line
                new BackupsModule(),
                new DecommissioningModule(),
                new CleanupsModule(),
                new UpgradeSSTablesModule(),
                new RebuildModule(),
                new ScrubModule()
        );

        return injector.getInstance(Application.class).call();
    }
}
