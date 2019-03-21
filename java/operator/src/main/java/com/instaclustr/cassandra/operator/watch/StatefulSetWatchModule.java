package com.instaclustr.cassandra.operator.watch;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.StatefulSetWatchEvent;
import com.instaclustr.cassandra.operator.model.key.StatefulSetKey;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.k8s.watch.WatchService;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta2StatefulSet;
import io.kubernetes.client.models.V1beta2StatefulSetList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

public class StatefulSetWatchModule extends AbstractModule {
    @Singleton
    static class StatefulSetCache extends ResourceCache<StatefulSetKey, V1beta2StatefulSet> {
        @Inject
        public StatefulSetCache(final EventBus eventBus, final StatefulSetWatchEvent.Factory eventFactory) {
            super(eventBus, eventFactory);
        }

        @Override
        protected StatefulSetKey resourceKey(final V1beta2StatefulSet statefulSet) {
            return StatefulSetKey.forStatefulSet(statefulSet);
        }

        @Override
        protected V1ObjectMeta resourceMetadata(final V1beta2StatefulSet statefulSet) {
            return statefulSet.getMetadata();
        }
    }

    @Singleton
    static class StatefulSetWatchService extends WatchService<V1beta2StatefulSet, V1beta2StatefulSetList, StatefulSetKey> {
        private final AppsV1beta2Api appsApi;
        private final String namespace;

        @Inject
        public StatefulSetWatchService(final ApiClient apiClient,
                                       final ResourceCache<StatefulSetKey, V1beta2StatefulSet> statefulSetCache,
                                       final AppsV1beta2Api appsApi,
                                       @Namespace final String namespace) {
            super(apiClient, statefulSetCache);

            this.appsApi = appsApi;
            this.namespace = namespace;
        }

        @Override
        protected Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException {
            return appsApi.listNamespacedStatefulSetCall(namespace, null, null, continueToken, null, "operator=instaclustr-cassandra-operator", null, resourceVersion, null, watch, null, null);
        }

        @Override
        protected Collection<? extends V1beta2StatefulSet> resourceListItems(final V1beta2StatefulSetList statefulSetList) {
            return statefulSetList.getItems();
        }

        @Override
        protected V1ListMeta resourceListMetadata(final V1beta2StatefulSetList statefulSetList) {
            return statefulSetList.getMetadata();
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<ResourceCache<StatefulSetKey, V1beta2StatefulSet>>() {}).to(StatefulSetCache.class);

        Multibinder.newSetBinder(binder(), Service.class).addBinding().to(StatefulSetWatchService.class);

        install(new FactoryModuleBuilder().build(StatefulSetWatchEvent.Factory.class));
    }
}
