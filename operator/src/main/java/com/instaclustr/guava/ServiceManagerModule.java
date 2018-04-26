package com.instaclustr.guava;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Set;

public class ServiceManagerModule extends AbstractModule {

    @Provides
    ServiceManager provideServiceManager(final Set<Service> services) {
        return new ServiceManager(services);
    }
}
