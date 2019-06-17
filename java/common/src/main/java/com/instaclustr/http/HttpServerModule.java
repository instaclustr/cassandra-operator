package com.instaclustr.http;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.guice.ServiceBindings;
import com.instaclustr.picocli.SidecarCLIOptions;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.InjectionManagerProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerModule.class);

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), HttpServerService.class);
    }

    @Provides
    @Singleton
    HttpServer provideHttpServer(final ResourceConfig resourceConfig, final SidecarCLIOptions cliOptions) {

        logger.info("Starting Sidecar HTTP server, listening on {}", cliOptions.httpServiceAddress);

        return JdkHttpServerFactory.createHttpServer(cliOptions.httpServiceAddress, resourceConfig, false);
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
                .register(guiceHK2BridgeFeature);
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
     * A HandlerInstantiator that forwards requests to Guice, to allow for injection into handlers
     */
    static class GuiceJacksonHandlerInstantiator extends HandlerInstantiator {
        private final Injector injector;

        @Inject
        public GuiceJacksonHandlerInstantiator(final Injector injector) {
            this.injector = injector;
        }

        @SuppressWarnings("unchecked")
        @Override
        public JsonDeserializer<?> deserializerInstance(final DeserializationConfig config, final Annotated annotated, final Class<?> deserClass) {
            return injector.getInstance((Class<? extends JsonDeserializer>) deserClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public KeyDeserializer keyDeserializerInstance(final DeserializationConfig config, final Annotated annotated, final Class<?> keyDeserClass) {
            return injector.getInstance((Class<? extends KeyDeserializer>) keyDeserClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public JsonSerializer<?> serializerInstance(final SerializationConfig config, final Annotated annotated, final Class<?> serClass) {
            return injector.getInstance((Class<? extends JsonSerializer>) serClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public TypeResolverBuilder<?> typeResolverBuilderInstance(final MapperConfig<?> config, final Annotated annotated, final Class<?> builderClass) {
            return injector.getInstance((Class<? extends TypeResolverBuilder>) builderClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public TypeIdResolver typeIdResolverInstance(final MapperConfig<?> config, final Annotated annotated, final Class<?> resolverClass) {
            return injector.getInstance((Class<? extends TypeIdResolver>) resolverClass);
        }
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
