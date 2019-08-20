package com.instaclustr.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;

import com.instaclustr.guice.GuiceInjectorHolder;

public final class GuiceInjectingConstraintValidatorFactory implements ConstraintValidatorFactory {

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(final Class<T> key) {
        try {
            return GuiceInjectorHolder.INSTANCE.getInjector().getInstance(key);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to instantiate constraint validator");
        }
    }

    @Override
    public void releaseInstance(final ConstraintValidator<?, ?> instance) {

    }
}
