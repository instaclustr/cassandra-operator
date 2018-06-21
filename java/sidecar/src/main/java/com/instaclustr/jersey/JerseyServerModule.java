package com.instaclustr.jersey;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.ws.rs.core.UriBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public class JerseyServerModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(JerseyServerModule.class);

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(HttpServerService.class);
    }

    @Provides
    HttpServer provideHttpServer(final ResourceConfig resourceConfig) throws UnknownHostException, URISyntaxException {
        // TODO: make URI configurable
        String protocol = "http://";
        String port = ":4567/";
        String hostname = InetAddress.getLocalHost().getHostName();
        if (hostname.equals("127.0.0.1"))
        {
            hostname = "localhost";
        }
        logger.info("Listening on " + protocol + hostname + port);

        return JdkHttpServerFactory.createHttpServer(UriBuilder.fromUri(protocol + hostname + port).build(), resourceConfig, false);
    }
}
