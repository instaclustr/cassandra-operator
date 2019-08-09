package com.instaclustr.sidecar.validation;

import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.ContextResolver;

import com.instaclustr.validation.GuiceInjectingConstraintValidatorFactory;
import org.glassfish.jersey.server.validation.ValidationConfig;

public class ValidationConfigurationContextResolver implements ContextResolver<ValidationConfig> {

    @Context
    private ResourceContext resourceContext;

    @Override
    public ValidationConfig getContext(final Class<?> type) {
        final ValidationConfig config = new ValidationConfig();
        config.constraintValidatorFactory(new GuiceInjectingConstraintValidatorFactory());
        return config;
    }
}
