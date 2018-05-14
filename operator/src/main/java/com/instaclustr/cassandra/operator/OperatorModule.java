package com.instaclustr.cassandra.operator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import com.google.inject.*;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.instaclustr.cassandra.operator.event.DataCenterEvent;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.cassandra.operator.service.CassandraHealthCheckService;
import com.instaclustr.cassandra.operator.service.CassandraRepairService;
import com.instaclustr.cassandra.operator.service.ControllerService;
import com.google.common.util.concurrent.Service;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.service.GarbageCollectorService;
import com.instaclustr.k8s.WatchConfig;
import com.instaclustr.k8s.WatchService;

import java.util.Set;

public class OperatorModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(ControllerService.class);
        serviceMultibinder.addBinding().to(GarbageCollectorService.class);
        serviceMultibinder.addBinding().to(CassandraHealthCheckService.class);

        serviceMultibinder.addBinding().to(DataCenterWatchService.class);
        serviceMultibinder.addBinding().to(CassandraRepairService.class);

//        serviceMultibinder.addBinding().toProvider(SecretWatchServiceProvider.class);


        bind(EventBus.class).asEagerSingleton();


//        bindListener(new AbstractMatcher<Binding<?>>() {
//            @Override
//            public boolean matches(final Binding<?> binding) {
//                binding.getKey().
//            }
//        }, new TypeListener() {
//            @Override
//            public <I> void hear(final TypeLiteral<I> type, final TypeEncounter<I> encounter) {
//                final Provider<EventBus> provider = encounter.getProvider(EventBus.class);
//
//                encounter.register(new InjectionListener<I>() {
//                    @Override
//                    public void afterInjection(final I injectee) {
//                        final EventBus eventBus = provider.get();
//
//                        System.out.println(eventBus);
//                    }
//                });
//            }
//        });


        bind(new TypeLiteral<Cache<DataCenterKey, DataCenter>>() {}).toInstance(CacheBuilder.newBuilder().build());

        install(new FactoryModuleBuilder().build(DataCenterEvent.Factory.class));

        final Multibinder<WatchConfig> watchConfigMultibinder = Multibinder.newSetBinder(binder(), WatchConfig.class);

        //watchConfigMultibinder.addBinding().toProvider(new)
    }

    static class SecretWatchServiceProvider implements Provider<WatchService> {

        @Override
        public WatchService get() {
            return null;
        }
    }
}
