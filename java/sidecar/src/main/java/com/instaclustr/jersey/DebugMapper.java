package com.instaclustr.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class DebugMapper implements ExceptionMapper<Throwable> {
    private static final Logger logger = LoggerFactory.getLogger(DebugMapper.class);

    @Override
    public Response toResponse(Throwable t) {
        logger.error("Encontered exception", t);
        return Response.serverError()
                .entity(t.getMessage())
                .build();
    }
}
