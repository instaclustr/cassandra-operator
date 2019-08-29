package com.instaclustr.sidecar.jersey;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.instaclustr.sidecar.jersey.OperationTypeIdParamConverterProvider.InvalidTypeIdException;

@Provider
public class InvalidTypeIdExceptionMapperProvider implements ExceptionMapper<InvalidTypeIdException> {

    @Override
    public Response toResponse(final InvalidTypeIdException exception) {
        return exception.getResponse();
    }
}
