package com.instaclustr.cassandra.sidecar;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.instaclustr.cassandra.sidecar.picocli.SidecarJarManifestVersionProvider;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.jersey.JerseyServerModule;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

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

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {
        logCommandVersionInformation(commandSpec);

        final Injector injector = Guice.createInjector(
                new ServiceManagerModule(),

                new SidecarModule(),
                new JerseyServerModule()

        );

        return injector.getInstance(Application.class).call();
    }
}
