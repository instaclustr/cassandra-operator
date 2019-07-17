package com.instaclustr.cassandra.sidecar;

import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;
import static com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter.DEFAULT_CASSANDRA_JMX_PORT;

import javax.management.remote.JMXServiceURL;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.instaclustr.cassandra.backup.guice.BackupRestoreModule;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupsModule;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissioningModule;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildModule;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubModule;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesModule;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import com.instaclustr.guice.Application;
import com.instaclustr.guice.ServiceManagerModule;
import com.instaclustr.measure.Time;
import com.instaclustr.picocli.CLIApplication;
import com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter;
import com.instaclustr.picocli.typeconverter.ServerInetSocketAddressTypeConverter;
import com.instaclustr.picocli.typeconverter.TimeMeasureTypeConverter;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.sidecar.operations.OperationsModule;
import jmx.org.apache.cassandra.JMXConnectionInfo;
import jmx.org.apache.cassandra.guice.CassandraModule;
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
public final class Sidecar extends CLIApplication implements Callable<Void> {

    private static final int DEFAULT_SIDECAR_HTTP_PORT = 4567;

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

    @CommandLine.Option(
            names = {"-e", "--operations-expiration"},
            description = "Period after which finished operations are deleted.",
            converter = TimeMeasureTypeConverter.class
    )
    public Time operationsExpirationPeriod = new Time((long) 1, TimeUnit.HOURS);

    @Spec
    private CommandLine.Model.CommandSpec commandSpec;

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        System.exit(execute(new Sidecar(), args));
    }

    @Override
    public Void call() throws Exception {

        logCommandVersionInformation(commandSpec);

        final Injector injector = Guice.createInjector(
                Stage.PRODUCTION, // production binds singletons as eager by default

                new ServiceManagerModule(),

                new CassandraModule(new JMXConnectionInfo(jmxPassword, jmxUser, jmxServiceURL)),
                new JerseyHttpServerModule(httpServerAddress),

                new OperationsModule(operationsExpirationPeriod),
                new DecommissioningModule(),
                new CleanupsModule(),
                new UpgradeSSTablesModule(),
                new RebuildModule(),
                new ScrubModule(),
                new BackupRestoreModule()
        );

        return injector.getInstance(Application.class).call();
    }
}
