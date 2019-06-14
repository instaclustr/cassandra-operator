package com.instaclustr.http;

import javax.inject.Inject;

import com.google.common.util.concurrent.AbstractIdleService;
import com.sun.net.httpserver.HttpServer;

/**
 * A Guava Service that manages a HttpServer
 */
public class HttpServerService extends AbstractIdleService {

    private final HttpServer httpServer;

    @Inject
    public HttpServerService(final HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    protected void startUp() throws Exception {
        httpServer.start();
    }

    @Override
    protected void shutDown() throws Exception {
        httpServer.stop(0);
    }
}
