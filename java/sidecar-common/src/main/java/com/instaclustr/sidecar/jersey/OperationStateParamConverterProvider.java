package com.instaclustr.sidecar.jersey;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;

import com.instaclustr.sidecar.operations.Operation;
import org.glassfish.jersey.server.validation.ValidationError;

@Provider
public class OperationStateParamConverterProvider implements ParamConverterProvider {

    @SuppressWarnings("unchecked")
    @Override
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType, final Annotation[] annotations) {
        if (rawType != Operation.State.class) {
            return null;
        }

        return (ParamConverter<T>) new ParamConverter<Operation.State>() {
            @Override
            public Operation.State fromString(final String value) {
                if (value == null) {
                    throw new InvalidOperationStateException(null);
                }

                try {
                    return Operation.State.valueOf(value.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    throw new InvalidOperationStateException(value);
                }
            }

            @Override
            public String toString(final Operation.State value) {
                if (value == null) {
                    throw new IllegalArgumentException();
                }

                return value.name();
            }
        };
    }

    static class InvalidOperationStateException extends WebApplicationException {

        private static Response buildResponse(final String invalidState) {
            final ValidationError validationError = new ValidationError();
            validationError.setInvalidValue(invalidState);
            validationError.setMessage("Operation state is invalid, possible states: " + Arrays.toString(Operation.State.values()));

            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(validationError)
                    .build();
        }

        public InvalidOperationStateException(final String invalidState) {
            super(buildResponse(invalidState));
        }
    }

    @Provider
    static class InvalidOperationStateExceptionMapper implements ExceptionMapper<InvalidOperationStateException> {
        @Override
        public Response toResponse(final InvalidOperationStateException exception) {
            return exception.getResponse();
        }
    }
}
