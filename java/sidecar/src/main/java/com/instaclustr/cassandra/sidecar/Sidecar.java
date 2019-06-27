package com.instaclustr.cassandra.sidecar;

import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;

import java.util.concurrent.Callable;

import com.google.inject.Guice;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.http.HttpServerModule;
import com.instaclustr.operations.OperationsModule;
import com.instaclustr.cassandra.sidecar.cassandra.CassandraModule;
import com.instaclustr.cassandra.sidecar.operations.backup.BackupsModule;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupsModule;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissioningModule;
import com.instaclustr.cassandra.sidecar.picocli.CassandraSidecarCLIOptions;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Spec;

@Command(name = "cassandra-sidecar",
        mixinStandardHelpOptions = true,
        description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
        versionProvider = SidecarJarManifestVersionProvider.class,
        sortOptions = false
)
public final class Sidecar implements Callable<Void> {

    @Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @Mixin
    private CassandraSidecarCLIOptions cliOptions;

    public static void main(final String[] args) {

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {

        logCommandVersionInformation(commandSpec);

        return Guice.createInjector(

                new SidecarModule(cliOptions),
                new ServiceManagerModule(),

                new CassandraModule(),
                new HttpServerModule(),

                new OperationsModule(),
                new BackupsModule(),
                new DecommissioningModule(),
                new CleanupsModule()
        ).getInstance(Application.class).call();
    }
}
