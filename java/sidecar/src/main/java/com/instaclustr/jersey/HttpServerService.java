package com.instaclustr.jersey;

import com.google.common.util.concurrent.AbstractIdleService;
import com.sun.net.httpserver.HttpServer;

import javax.inject.Inject;

/**
 * A Guava Service that manages a HttpServer
 */
public class HttpServerService extends AbstractIdleService {
    private static final int SERVER_SHUTDOWN_DELAY_SECONDS = 10;

    private final HttpServer server;

    @Inject
    public HttpServerService(final HttpServer server) {
        this.server = server;
    }

    @Override
    protected void startUp() throws Exception {
        server.start();
    }

    @Override
    protected void shutDown() throws Exception {
        server.stop(SERVER_SHUTDOWN_DELAY_SECONDS);
    }
}
