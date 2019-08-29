package com.instaclustr.sidecar.http;

import javax.ws.rs.core.Application;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractIdleService;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;
import org.glassfish.jersey.jdkhttp.JdkHttpHandlerContainer;
import org.glassfish.jersey.jdkhttp.JdkHttpHandlerContainerProvider;
import org.glassfish.jersey.process.JerseyProcessingUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Guava Service that manages a HttpServer for a Jersey application
 */
public class JerseyHttpServerService extends AbstractIdleService {
    private static final Logger logger = LoggerFactory.getLogger(JerseyHttpServerService.class);

    private final JdkHttpHandlerContainer container;
    private final HttpServer httpServer;

    public JerseyHttpServerService(final InetSocketAddress httpServerAddress, final Application application) {
        container = new JdkHttpHandlerContainerProvider().createContainer(JdkHttpHandlerContainer.class, application);

        try {
            httpServer = HttpServer.create(httpServerAddress, 0);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        httpServer.setExecutor(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setNameFormat("jdk-http-server-%d")
                .setUncaughtExceptionHandler(new JerseyProcessingUncaughtExceptionHandler())
                .build()));

        httpServer.createContext("/", container);
    }

    @Override
    protected void startUp() throws Exception {
        httpServer.start();
        container.getApplicationHandler().onStartup(container);

        if (logger.isInfoEnabled()) {
            final InetSocketAddress socketAddress = httpServer.getAddress();
            final URI serverUrl = new URI("http",
                                          null,
                                          InetAddresses.toUriString(socketAddress.getAddress()), socketAddress.getPort(),
                                          "/",
                                          null,
                                          null);

            logger.info("Started HTTP server on {}", serverUrl);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        container.getApplicationHandler().onShutdown(container);
        httpServer.stop(0);
    }
}
