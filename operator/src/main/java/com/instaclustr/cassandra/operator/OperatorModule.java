package com.instaclustr.cassandra.operator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.instaclustr.cassandra.operator.event.DataCenterEvent;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.cassandra.operator.service.ControllerService;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.service.GarbageCollectorService;
import com.instaclustr.k8s.WatchService;

import java.util.Set;

public class OperatorModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(ControllerService.class);
        serviceMultibinder.addBinding().to(GarbageCollectorService.class);
        serviceMultibinder.addBinding().to(WatchService.class);

        bind(EventBus.class).toInstance(new EventBus());

        bind(new TypeLiteral<Cache<DataCenterKey, DataCenter>>() {}).toInstance(CacheBuilder.newBuilder().build());

        install(new FactoryModuleBuilder().build(DataCenterEvent.Factory.class));
    }
}
