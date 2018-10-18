package com.instaclustr.cassandra.operator.modules;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.StatefulSetWatchEvent;
import com.instaclustr.cassandra.operator.model.key.StatefulSetKey;
import com.instaclustr.k8s.watch.ListCallProvider;
import com.instaclustr.k8s.watch.WatchService;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.models.V1beta2StatefulSet;
import io.kubernetes.client.models.V1beta2StatefulSetList;

import java.util.HashMap;
import java.util.Map;

public class StatefulSetWatchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(new TypeLiteral<Map<StatefulSetKey, V1beta2StatefulSet>>() {}).toInstance(new HashMap<>());

        install(new FactoryModuleBuilder().build(StatefulSetWatchEvent.Factory.class));
    }

    @ProvidesIntoSet
    Service provideStatefulSetWatchService(final ApiClient apiClient, final AppsV1beta2Api appsApi,
                                           final EventBus eventBus, final StatefulSetWatchEvent.Factory statefulSetWatchEventFactory,
                                           final Map<StatefulSetKey, V1beta2StatefulSet> cache,
                                           @Namespace final String namespace) {
        final ListCallProvider listCallProvider = (resourceVersion, watch) ->
                appsApi.listNamespacedStatefulSetCall(namespace, null, null, null, null, "operator=instaclustr-cassandra-operator", null, resourceVersion, null, watch, null, null);

        return new WatchService<V1beta2StatefulSet, V1beta2StatefulSetList, StatefulSetKey>(apiClient, eventBus,
                listCallProvider, statefulSetWatchEventFactory,
                cache,
                StatefulSetKey::forStatefulSet,
                V1beta2StatefulSet::getMetadata,
                V1beta2StatefulSetList::getMetadata,
                V1beta2StatefulSetList::getItems
        ) {};
    }

}
