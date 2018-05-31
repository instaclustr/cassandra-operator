package com.instaclustr.cassandra.operator.modules;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.instaclustr.cassandra.operator.event.ConfigMapWatchEvent;
import com.instaclustr.cassandra.operator.event.StatefulSetWatchEvent;
import com.instaclustr.cassandra.operator.model.key.ConfigMapKey;
import com.instaclustr.cassandra.operator.model.key.StatefulSetKey;
import com.instaclustr.k8s.watch.ListCallProvider;
import com.instaclustr.k8s.watch.WatchService;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreApi;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1beta2StatefulSet;
import io.kubernetes.client.models.V1beta2StatefulSetList;

import java.util.HashMap;
import java.util.Map;

public class ConfigMapWatchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(new TypeLiteral<Map<ConfigMapKey, V1ConfigMap>>() {}).toInstance(new HashMap<>());

        install(new FactoryModuleBuilder().build(ConfigMapWatchEvent.Factory.class));
    }

    @ProvidesIntoSet
    Service provideConfigMapWatchService(final ApiClient apiClient, final CoreV1Api coreApi,
                                         final EventBus eventBus, final ConfigMapWatchEvent.Factory configMapWatchEventFactory,
                                         final Map<ConfigMapKey, V1ConfigMap> cache) {

        // TODO: paramaterise namespace & add a label selector only caring about statefulsets that represent C* clusters
        final ListCallProvider listCallProvider = (resourceVersion, watch) ->
                coreApi.listNamespacedConfigMapCall("default", null, null, null, null, null, null, resourceVersion, null, watch, null, null);

        return new WatchService<V1ConfigMap, V1ConfigMapList, ConfigMapKey>(apiClient, eventBus,
                listCallProvider, configMapWatchEventFactory,
                cache,
                ConfigMapKey::forConfigMap,
                V1ConfigMap::getMetadata,
                V1ConfigMapList::getMetadata,
                V1ConfigMapList::getItems
        ) {};
    }

}
