package com.instaclustr.sidecar.jersey;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class DefaultExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultExceptionMapper.class);

    @Override
    public Response toResponse(final Throwable t) {
        logger.error("Encountered exception", t);
        return Response.serverError().entity(t.getMessage()).build();
    }
}
