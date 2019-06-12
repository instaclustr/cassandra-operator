package com.instaclustr.jersey;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.cassandra.sidecar.jackson.GuiceHandlerInstantiator;
import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;
import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.operation.OperationType;
import com.instaclustr.guice.ServiceBindings;
import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JerseyServerModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(JerseyServerModule.class);

    private URI httpServerUri;

    public JerseyServerModule(final URI httpServiceUri) {
        this.httpServerUri = httpServiceUri;
    }

    @Override
    protected void configure() {
        ServiceBindings.bindService(binder(), HttpServerService.class);
    }

    @Provides
    HttpServer provideHttpServer(final ResourceConfig resourceConfig) {

        logger.info("Listening on " + httpServerUri);

        return JdkHttpServerFactory.createHttpServer(httpServerUri, resourceConfig, false);
    }

    @Singleton
    @Provides
    ObjectMapper provideObjectMapper(final GuiceHandlerInstantiator guiceHandlerInstantiator) {

        final SimpleModule module = new SimpleModule("OperationDeserializerModule");
        module.addDeserializer(Operation.class, new OperationDeserializer());

        final ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setHandlerInstantiator(guiceHandlerInstantiator);

        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);

        objectMapper.registerModule(module);
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }

    /**
     * Implementation of feature where overriding of built-in ObjectMapper is happening.
     * There is autodiscovery of JacksonFeature because it is on the class path from jersey-media-json-jackson
     * which provides its default ObjectMapper hence if we want to provide our own, this is the standard way how to do that.
     */
    public static class MarshallingFeature implements Feature {

        private final ObjectMapper objectMapper;

        @Inject
        public MarshallingFeature(final ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public boolean configure(FeatureContext context) {

            final JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider(objectMapper, JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS);

            context.register(provider);
            return true;
        }
    }

    public static class OperationDeserializer extends StdDeserializer<Operation> {

        public OperationDeserializer() {
            super(Operation.class);
        }

        @Override
        public Operation deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

            final ObjectMapper objectMapper = (ObjectMapper) jsonParser.getCodec();

            final ObjectNode root = objectMapper.readTree(jsonParser);

            final JsonNode type = root.get("type");

            if (type == null) {
                throw new OperationDeserializationException("Object to deserialize does not have field 'type'");
            }

            final OperationType operationType = OperationType.valueOf(type.asText().toUpperCase());

            switch (operationType) {
                case BACKUP:
                    return objectMapper.readValue(root.toString(), BackupOperation.class);
                case DECOMMISSION: {
                    return objectMapper.readValue(root.toString(), DecommissionOperation.class);
                }
                default:
                    throw new OperationDeserializationException(String.format("Deserialization of type %s is not supported yet.", type));
            }
        }
    }

    public static final class OperationDeserializationException extends IOException {

        public OperationDeserializationException(String message) {
            super(message);
        }
    }
}
