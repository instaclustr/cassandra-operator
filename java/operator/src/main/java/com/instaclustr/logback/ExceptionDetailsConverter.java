package com.instaclustr.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.pattern.DynamicConverter;
import io.kubernetes.client.ApiException;

public class ExceptionDetailsConverter extends DynamicConverter<ILoggingEvent> {
    @Override
    public String convert(final ILoggingEvent event) {
        final IThrowableProxy throwableProxy = event.getThrowableProxy();

        if (throwableProxy == null)
            return "";

        Throwable throwable = ((ThrowableProxy) throwableProxy).getThrowable();

        final StringBuilder output = new StringBuilder();

        while (throwable != null) {
            if (throwable instanceof ApiException) {
                final ApiException apiException = (ApiException) throwable;

                output.append(String.format("Response details of %s:%n", apiException.toString()));
                output.append(String.format("\tHTTP response code: %d%n", apiException.getCode()));
                output.append(String.format("\tHTTP response headers: %s%n", apiException.getResponseHeaders()));
                output.append(String.format("\tHTTP response body: %s%n", apiException.getResponseBody()));
            }

            throwable = throwable.getCause();
        }

        return output.toString();
    }
}
