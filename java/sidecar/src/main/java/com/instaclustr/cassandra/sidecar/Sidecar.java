package com.instaclustr.cassandra.sidecar;

import static com.google.inject.Guice.createInjector;
import static com.google.inject.Stage.PRODUCTION;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.instaclustr.cassandra.CassandraModule;
import com.instaclustr.cassandra.backup.guice.StorageModules;
import com.instaclustr.cassandra.backup.impl.backup.BackupModules.BackupModule;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupsModule;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissioningModule;
import com.instaclustr.cassandra.sidecar.operations.drain.DrainModule;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildModule;
import com.instaclustr.cassandra.sidecar.operations.restart.RestartModule;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubModule;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesModule;
import com.instaclustr.cassandra.sidecar.service.ServicesModule;
import com.instaclustr.guice.Application;
import com.instaclustr.guice.ServiceManagerModule;
import com.instaclustr.operations.OperationsModule;
import com.instaclustr.picocli.CLIApplication;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.sidecar.picocli.SidecarSpec;
import com.instaclustr.threading.ExecutorsModule;
import com.instaclustr.version.VersionModule;
import jmx.org.apache.cassandra.CassandraJMXConnectionInfo;
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

        // production binds singletons as eager by default
        final Injector injector = createInjector(PRODUCTION, getModules());

        return injector.getInstance(Application.class).call();
    }

    @Override
    public String getImplementationTitle() {
        return "cassandra-sidecar";
    }

    public List<AbstractModule> getModules() throws Exception {
        List<AbstractModule> modules = new ArrayList<>();

        modules.addAll(backupModules());
        modules.addAll(operationModules());
        modules.addAll(sidecarModules());

        return modules;
    }

    public List<AbstractModule> sidecarModules() throws Exception {
        return new ArrayList<AbstractModule>() {{
            add(new VersionModule(getVersion()));
            add(new ServiceManagerModule());
            add(new CassandraModule(new CassandraJMXConnectionInfo(jmxSpec.jmxPassword,
                                                                   jmxSpec.jmxUser,
                                                                   jmxSpec.jmxServiceURL,
                                                                   jmxSpec.trustStore,
                                                                   jmxSpec.trustStorePassword)));
            add(new JerseyHttpServerModule(sidecarSpec.httpServerAddress));
            add(new OperationsModule(sidecarSpec.operationsExpirationPeriod));
            add(new ExecutorsModule());
            add(new ServicesModule());
        }};
    }

    public static List<AbstractModule> backupModules() {
        return new ArrayList<AbstractModule>() {{
            add(new StorageModules());
            add(new BackupModule());
        }};
    }

    public static List<AbstractModule> operationModules() {
        return new ArrayList<AbstractModule>() {{
            add(new DecommissioningModule());
            add(new CleanupsModule());
            add(new UpgradeSSTablesModule());
            add(new RebuildModule());
            add(new ScrubModule());
            add(new DrainModule());
            add(new RestartModule());
        }};
    }
}
