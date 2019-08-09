package com.instaclustr.guice;

import com.google.inject.Injector;

/**
 * Holds injector so we have access to it from
 * places where it can not be injected, for example into
 * ConstraintValidators so validators can inject objects
 * from Guice.
 */
public enum GuiceInjectorHolder {
    INSTANCE;

    private Injector injector;

    public void setInjector(final Injector injector) {
        this.injector = injector;
    }

    public Injector getInjector() {
        return injector;
    }
}
