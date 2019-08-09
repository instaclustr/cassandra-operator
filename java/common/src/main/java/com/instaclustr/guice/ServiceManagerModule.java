package com.instaclustr.guice;

import java.util.Set;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class ServiceManagerModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Service.class);
    }

    @Provides
    ServiceManager provideServiceManager(final Set<Service> services) {
        return new ServiceManager(services);
    }
}
