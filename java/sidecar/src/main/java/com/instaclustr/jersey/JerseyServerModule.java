package com.instaclustr.jersey;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;


import javax.ws.rs.core.UriBuilder;

public class JerseyServerModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(HttpServerService.class);
    }

    @Provides
    HttpServer provideHttpServer(final ResourceConfig resourceConfig) {
        // TODO: make URI configurable
        return JdkHttpServerFactory.createHttpServer(UriBuilder.fromUri("http://localhost:4567/").build(), resourceConfig, false);
    }
}
