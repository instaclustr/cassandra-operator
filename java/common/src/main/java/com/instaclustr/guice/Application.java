package com.instaclustr.guice;

import javax.inject.Inject;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Application is a collection of Services, managed by a ServiceManager.
 */
public class Application implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private final ServiceManager serviceManager;

    @Inject
    public Application(final ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    public Void call() throws Exception {
        logger.info("Services to start: {}", serviceManager.servicesByState().get(Service.State.NEW));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down {}.", serviceManager.servicesByState().get(Service.State.RUNNING));

            serviceManager.stopAsync();

            while (true) {
                try {
                    serviceManager.awaitStopped(10, TimeUnit.SECONDS);
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

        return null;
    }
}
