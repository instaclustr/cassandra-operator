package com.instaclustr.cassandra.operator.sidecar;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class SidecarClientModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SidecarClientFactory.class).in(Singleton.class);
    }
}
