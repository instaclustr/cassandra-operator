package com.instaclustr.cassandra.sidecar;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.instaclustr.cassandra.sidecar.resource.BackupsResource;
import com.instaclustr.cassandra.sidecar.resource.RepairsResource;
import com.instaclustr.cassandra.sidecar.service.backup.BackupService;
import com.instaclustr.jersey.hk2.GuiceHK2BridgeFeature;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class SidecarModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(BackupService.class).asEagerSingleton();

        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(BackupService.class);
    }

    @Provides
    ResourceConfig provideResourceConfig(final GuiceHK2BridgeFeature guiceHK2BridgeFeature) {
        return new ResourceConfig(BackupsResource.class, RepairsResource.class)
                .register(JacksonFeature.class)
                .register(guiceHK2BridgeFeature);
    }
}
