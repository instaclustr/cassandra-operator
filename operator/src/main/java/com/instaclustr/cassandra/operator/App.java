package com.instaclustr.cassandra.operator;


import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.inject.*;
import com.instaclustr.cassandra.operator.preflight.Preflight;
import com.instaclustr.cassandra.operator.preflight.PreflightModule;
import com.instaclustr.k8s.K8sModule;
import io.kubernetes.client.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private static class ThreadBackstop implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(final Thread thread, final Throwable throwable) {
            System.err.format("Uncaught exception in thread %s%n", thread.getName());
            throwable.printStackTrace();
        }
    }

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Thread.setDefaultUncaughtExceptionHandler(new ThreadBackstop());
    }

    public static void main(String[] args) throws Exception {
        final Injector injector = Guice.createInjector(new K8sModule(), new PreflightModule(), new OperatorModule());

        // run Preflight operations
        injector.getInstance(Preflight.class).run();

        // TODO: refactor into separate method, maybe...
        {
            final Set<Service> services = injector.getInstance(Key.get(new TypeLiteral<Set<Service>>() {}));
            final ServiceManager serviceManager = new ServiceManager(services);

            logger.info("Services to start: {}", services);

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

            // never return
            serviceManager.awaitStopped();
        }
    }
}
