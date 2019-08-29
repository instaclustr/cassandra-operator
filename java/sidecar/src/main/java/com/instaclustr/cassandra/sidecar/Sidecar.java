package com.instaclustr.cassandra.sidecar;

import java.util.concurrent.Callable;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.instaclustr.cassandra.backup.aws.S3Module;
import com.instaclustr.cassandra.backup.azure.AzureModule;
import com.instaclustr.cassandra.backup.gcp.GCPModule;
import com.instaclustr.cassandra.backup.impl.backup.BackupModule;
import com.instaclustr.cassandra.backup.local.LocalFileModule;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupsModule;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissioningModule;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildModule;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubModule;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesModule;
import com.instaclustr.guice.Application;
import com.instaclustr.guice.ServiceManagerModule;
import com.instaclustr.picocli.CLIApplication;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.operations.OperationsModule;
import com.instaclustr.sidecar.picocli.SidecarSpec;
import com.instaclustr.threading.ExecutorsModule;
import com.instaclustr.version.VersionModule;
import jmx.org.apache.cassandra.JMXConnectionInfo;
import jmx.org.apache.cassandra.guice.CassandraModule;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "cassandra-sidecar",
         mixinStandardHelpOptions = true,
         description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
         versionProvider = Sidecar.class,
         sortOptions = false
)
public final class Sidecar extends CLIApplication implements Callable<Void> {

    @Mixin
    private SidecarSpec sidecarSpec;

    @Mixin
    private CassandraJMXSpec jmxSpec;

    @Spec
    private CommandSpec commandSpec;

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

                new VersionModule(getVersion()),
                new ServiceManagerModule(),

                new CassandraModule(new JMXConnectionInfo(jmxSpec.jmxPassword,
                                                          jmxSpec.jmxUser,
                                                          jmxSpec.jmxServiceURL,
                                                          jmxSpec.trustStore,
                                                          jmxSpec.trustStorePassword)),
                new JerseyHttpServerModule(sidecarSpec.httpServerAddress),

                new OperationsModule(sidecarSpec.operationsExpirationPeriod),
                new DecommissioningModule(),
                new CleanupsModule(),
                new UpgradeSSTablesModule(),
                new RebuildModule(),
                new ScrubModule(),
                // backups modules
                new S3Module(),
                new AzureModule(),
                new GCPModule(),
                new LocalFileModule(),
                new BackupModule(),
                new ExecutorsModule()
        );

        return injector.getInstance(Application.class).call();
    }

    @Override
    public String getImplementationTitle() {
        return "cassandra-sidecar";
    }
}
