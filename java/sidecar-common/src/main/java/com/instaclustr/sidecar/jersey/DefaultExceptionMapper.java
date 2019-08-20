package com.instaclustr.sidecar.jersey;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(final Throwable exception) {
        return Response.serverError().entity(exception.getMessage()).build();
    }
}
