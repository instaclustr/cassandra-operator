package com.instaclustr.guice;

import com.google.common.util.concurrent.Service;
import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public final class ServiceBindings {
    private ServiceBindings() {}

    public static void bindService(final Binder binder, final Class<? extends Service> serviceClass) {
        binder.bind(serviceClass).asEagerSingleton(); // ensure only one copy of the Service exists

        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder, Service.class);

        serviceMultibinder.addBinding().to(serviceClass);
    }
}
