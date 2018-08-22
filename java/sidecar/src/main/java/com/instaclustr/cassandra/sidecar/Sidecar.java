package com.instaclustr.cassandra.sidecar;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.instaclustr.build.Info;
import com.instaclustr.guava.Application;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.jersey.JerseyServerModule;
import com.instaclustr.picocli.GitPropertiesVersionProvider;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "cassandra-sidecar",
        mixinStandardHelpOptions = true,
        description = "Sidecar management application for Apache Cassandra running on Kubernetes.",
        versionProvider = GitPropertiesVersionProvider.class,
        sortOptions = false
)
public class Sidecar implements Callable<Void> {
    public static void main(final String[] args) {
        CommandLine.call(new Sidecar(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {
        Info.logVersionInfo();

        final Injector injector = Guice.createInjector(
                new ServiceManagerModule(),

                new SidecarModule(),
                new JerseyServerModule()

        );

        return injector.getInstance(Application.class).call();
    }
}
