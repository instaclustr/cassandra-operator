package com.instaclustr.cassandra.operator.modules;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.instaclustr.cassandra.operator.event.ClusterWatchEvent;
import com.instaclustr.cassandra.operator.event.DataCenterWatchEvent;
import com.instaclustr.cassandra.operator.model.Cluster;
import com.instaclustr.cassandra.operator.model.ClusterList;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.DataCenterList;
import com.instaclustr.cassandra.operator.model.key.ClusterKey;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.k8s.watch.ListCallProvider;
import com.instaclustr.k8s.watch.WatchService;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CustomObjectsApi;

import java.util.HashMap;
import java.util.Map;

public class ClusterWatchModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(new TypeLiteral<Map<ClusterKey, Cluster>>() {}).toInstance(new HashMap<>());

        install(new FactoryModuleBuilder().build(ClusterWatchEvent.Factory.class));
    }

    @ProvidesIntoSet
    Service provideDataCenterWatchService(final ApiClient apiClient, final CustomObjectsApi customObjectsApi,
                                          final EventBus eventBus, final ClusterWatchEvent.Factory clusterWatchEventFactory,
                                          final Map<ClusterKey, Cluster> cache) {

        // TODO: parameterise namespace
        final ListCallProvider listCallProvider = (resourceVersion, watch) ->
                customObjectsApi.listNamespacedCustomObjectCall("stable.instaclustr.com", "v1", "default", "cassandra-clusters", null, null, resourceVersion, watch, null, null);

        return new WatchService<Cluster, ClusterList, ClusterKey>(
                apiClient, eventBus,
                listCallProvider, clusterWatchEventFactory,
                cache,
                ClusterKey::forCluster,
                Cluster::getMetadata,
                ClusterList::getMetadata,
                ClusterList::getItems
        ) {};
    }
}
