package com.instaclustr.sidecar.http;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.net.InetSocketAddress;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.instaclustr.sidecar.jackson.GuiceJacksonHandlerInstantiator;
import com.instaclustr.sidecar.validation.ValidationConfigurationContextResolver;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.InjectionManagerProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JerseyHttpServerModule extends AbstractModule {
    private InetSocketAddress httpServerAddress;

    public JerseyHttpServerModule(final InetSocketAddress httpServerAddress) {
        this.httpServerAddress = httpServerAddress;
    }

    public JerseyHttpServerModule() {
        // for testing
    }

    @Override
    protected void configure() {
//        ServiceBindings.bindService(binder(), JerseyHttpServerService.class);
    }

    @ProvidesIntoSet()
    @Singleton
    Service provideHttpServerService(final ResourceConfig resourceConfig) {
        return new JerseyHttpServerService(httpServerAddress, resourceConfig);
    }

    @Provides
    @Singleton
    ResourceConfig provideResourceConfig(final GuiceHK2BridgeFeature guiceHK2BridgeFeature,
                                         final CustomObjectMapperFeature customObjectMapperFeature,
                                         final DebugMapper debugMapper) {
        return new ResourceConfig()
                .packages("com.instaclustr")
                .register(customObjectMapperFeature)
                .register(debugMapper)
                .register(guiceHK2BridgeFeature)
                .register(ValidationConfigurationContextResolver.class)
                .property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
    }

    @Singleton
    @Provides
    ObjectMapper provideObjectMapper(final GuiceJacksonHandlerInstantiator guiceJacksonHandlerInstantiator) {

        final ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setHandlerInstantiator(guiceJacksonHandlerInstantiator);

        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);

        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }

    /**
     * Implementation of feature where overriding of built-in ObjectMapper is happening.
     * There is auto discovery of JacksonFeature because it is on the class path from jersey-media-json-jackson
     * which provides its default ObjectMapper hence if we want to provide our own, this is the standard way how to do that.
     */
    static class CustomObjectMapperFeature implements Feature {
        private final ObjectMapper objectMapper;

        @Inject
        public CustomObjectMapperFeature(final ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean configure(final FeatureContext context) {
            context.register(new JacksonJaxbJsonProvider(objectMapper, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS));
            return true;
        }
    }

    static class GuiceHK2BridgeFeature implements Feature {
        private final Injector injector;

        @Inject
        public GuiceHK2BridgeFeature(final Injector injector) {
            this.injector = injector;
        }

        @Override
        public boolean configure(final FeatureContext context) {

            final ServiceLocator serviceLocator = InjectionManagerProvider.getInjectionManager(context).getInstance(ServiceLocator.class);

            GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);

            serviceLocator.getService(GuiceIntoHK2Bridge.class).bridgeGuiceInjector(injector);

            return true;
        }
    }

    static class DebugMapper implements ExceptionMapper<Throwable> {
        private static final Logger logger = LoggerFactory.getLogger(DebugMapper.class);

        @Override
        public Response toResponse(final Throwable t) {
            logger.error("Encountered exception", t);
            return Response.serverError().entity(t.getMessage()).build();
        }
    }
}
