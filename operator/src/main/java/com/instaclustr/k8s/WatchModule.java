package com.instaclustr.k8s;

import com.google.inject.AbstractModule;
import com.google.inject.PrivateModule;

public abstract class WatchModule extends AbstractModule {

    protected abstract void configureWatch();

    @Override
    protected void configure() {
        install(new PrivateModule() {
            @Override
            protected void configure() {

            }
        });
    }
}
