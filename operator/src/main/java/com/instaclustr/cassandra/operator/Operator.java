package com.instaclustr.cassandra.operator;

import ch.qos.logback.classic.Level;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.*;
import com.instaclustr.cassandra.operator.preflight.Preflight;
import com.instaclustr.cassandra.operator.preflight.PreflightModule;
import com.instaclustr.guava.ServiceManagerModule;
import com.instaclustr.k8s.K8sModule;
import com.instaclustr.picocli.typeconverter.ExistingFilePathTypeConverter;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Command(name = "cassandra-operator", mixinStandardHelpOptions = true, version = "cassandra-operator 0.1.0",
         description = "A Kubernetes operator for Apache Cassandra.",
         versionProvider = Operator.ManifestVersionProvider.class,
         sortOptions = false)
public class Operator implements Callable<Void> {
    static final Logger logger = LoggerFactory.getLogger(Operator.class);

    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[]{"Version information not available"}; // TODO: load from MANIFEST.MF (and insert build/commit details into manifest on mvn pkg)
        }
    }

    static class OperatorOptions {
        @Option(names = {"-v", "--verbose"}, description = {"Be verbose.", "Specify @|italic --verbose|@ multiple times to increase verbosity."})
        boolean[] verbosity;

        @Option(names = {"-n", "--namespace"}, description = "")
        String namespace;
    }

    public static class K8sClientOptions {
        @CommandLine.Option(names = {"-c", "--kube-config"},
                converter = ExistingFilePathTypeConverter.class,
                description = {"Path to the Kubernetes client configuration file.",
                        "To prevent any config file from being used, including the default, set to empty (@|italic --kube-config|@=\"\") or use @|italic --no-kube-config|@."},
                showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
        Path kubeConfig = Paths.get(System.getenv(KubeConfig.ENV_HOME), KubeConfig.KUBEDIR, KubeConfig.KUBECONFIG);

        @CommandLine.Option(names = "--no-kube-config")
        boolean noKubeConfig;


        @CommandLine.Option(names = "--host")
        String host;

        @CommandLine.Option(names = "--insecure-tls")
        boolean disableTlsVerification;
    }

    @Mixin
    OperatorOptions operatorOptions;

    @Mixin
    K8sClientOptions k8sClientOptions;

    @Mixin
    K8sVersionValidator.Options versionValidatorOptions;


    public static void main(String[] args) {
        CommandLine.call(new Operator(), System.err, CommandLine.Help.Ansi.ON, args);
    }

    @Override
    public Void call() throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        final ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.instaclustr.cassandra.operator");
        rootLogger.setLevel(Level.DEBUG);


//        final KubeConfig kubeConfig;
//        try (var bufferedReader = Files.newBufferedReader(k8sClientOptions.kubeConfig)) {
//            kubeConfig = KubeConfig.loadKubeConfig(bufferedReader);
//        }


        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
//                        bind(KubeConfig.class).toInstance(kubeConfig);
                        try {
                            bind(ClientBuilder.class).toInstance(ClientBuilder.standard());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new ServiceManagerModule(),
                new K8sModule(),
                new PreflightModule(),
                new OperatorModule()
        );

        injector.getInstance(K8sVersionValidator.class).call(); // TODO: maybe make preflight, but requires ordering since PF operations can use the K8s client -- this need to be done first

        // run Preflight operations
        injector.getInstance(Preflight.class).run();

        runServices(injector);

        return null;
    }


    private void runServices(final Injector injector) throws TimeoutException {
        final ServiceManager serviceManager = injector.getInstance(ServiceManager.class);

        // TODO: this probably belongs somewhere else
        {
            final EventBus eventBus = injector.getInstance(EventBus.class);

            for (final Service service : serviceManager.servicesByState().values()) {
                eventBus.register(service);
            }
        }


        logger.info("Services to start: {}", serviceManager.servicesByState().get(Service.State.NEW));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down {}.", serviceManager.servicesByState().get(Service.State.RUNNING));

            serviceManager.stopAsync();

            while (true) {
                try {
                    serviceManager.awaitStopped(1, TimeUnit.MINUTES);
                    break;

                } catch (final TimeoutException e) {
                    logger.warn("Timeout waiting for {} to stop. Retrying.", serviceManager.servicesByState().get(Service.State.STOPPING), e);
                }
            }

            logger.info("Successfully shut down all services.");
        }, "ServiceManager Shutdown Hook"));

        // add a listener to catch any service failures
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(final Service service) {
                logger.error("Service {} failed. Shutting down.", service, service.failureCause());
                System.exit(1);
            }
        });

        try {
            logger.info("Starting services.");
            serviceManager.startAsync().awaitHealthy(1, TimeUnit.MINUTES);
            logger.info("Successfully started all services.");

        } catch (final TimeoutException e) {
            logger.error("Timeout waiting for {} to start.", serviceManager.servicesByState().get(Service.State.STARTING));
            throw e;

        } catch (final IllegalStateException e) {
            logger.error("Services {} failed to start.", serviceManager.servicesByState().get(Service.State.FAILED));
            throw e;
        }

        serviceManager.awaitStopped();
    }
}
