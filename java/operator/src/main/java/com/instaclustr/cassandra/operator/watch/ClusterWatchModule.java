package com.instaclustr.cassandra.operator.watch;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.ClusterWatchEvent;
import com.instaclustr.cassandra.operator.model.Cluster;
import com.instaclustr.cassandra.operator.model.ClusterList;
import com.instaclustr.cassandra.operator.model.key.ClusterKey;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.k8s.watch.WatchService;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

public class ClusterWatchModule extends AbstractModule {
    @Singleton
    static class ClusterCache extends ResourceCache<ClusterKey, Cluster> {
        @Inject
        public ClusterCache(final EventBus eventBus, final ClusterWatchEvent.Factory eventFactory) {
            super(eventBus, eventFactory);
        }

        @Override
        protected ClusterKey resourceKey(final Cluster cluster) {
            return ClusterKey.forCluster(cluster);
        }

        @Override
        protected V1ObjectMeta resourceMetadata(final Cluster cluster) {
            return cluster.getMetadata();
        }
    }

    @Singleton
    static class ClusterWatchService extends WatchService<Cluster, ClusterList, ClusterKey> {
        private final CustomObjectsApi customObjectsApi;
        private final String namespace;

        @Inject
        public ClusterWatchService(final ApiClient apiClient,
                                   final ResourceCache<ClusterKey, Cluster> clusterCache,
                                   final CustomObjectsApi customObjectsApi,
                                   @Namespace final String namespace) {
            super(apiClient, clusterCache);

            this.customObjectsApi = customObjectsApi;
            this.namespace = namespace;
        }

        @Override
        protected Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException {
            return customObjectsApi.listNamespacedCustomObjectCall("stable.instaclustr.com", "v1", namespace, "cassandra-clusters", null, null, resourceVersion, watch, null, null);
        }

        @Override
        protected Collection<? extends Cluster> resourceListItems(final ClusterList clusterList) {
            return clusterList.getItems();
        }

        @Override
        protected V1ListMeta resourceListMetadata(final ClusterList clusterList) {
            return clusterList.getMetadata();
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<ResourceCache<ClusterKey, Cluster>>() {}).to(ClusterCache.class);

        Multibinder.newSetBinder(binder(), Service.class).addBinding().to(ClusterWatchService.class);

        install(new FactoryModuleBuilder().build(ClusterWatchEvent.Factory.class));
    }
}
