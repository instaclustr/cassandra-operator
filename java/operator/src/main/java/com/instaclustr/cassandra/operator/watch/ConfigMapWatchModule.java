package com.instaclustr.cassandra.operator.watch;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.ConfigMapWatchEvent;
import com.instaclustr.cassandra.operator.model.key.ConfigMapKey;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.k8s.watch.WatchService;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

public class ConfigMapWatchModule extends AbstractModule {
    @Singleton
    static class ConfigMapCache extends ResourceCache<ConfigMapKey, V1ConfigMap> {
        @Inject
        public ConfigMapCache(final EventBus eventBus, final ConfigMapWatchEvent.Factory eventFactory) {
            super(eventBus, eventFactory);
        }

        @Override
        protected ConfigMapKey resourceKey(final V1ConfigMap configMap) {
            return ConfigMapKey.forConfigMap(configMap);
        }

        @Override
        protected V1ObjectMeta resourceMetadata(final V1ConfigMap configMap) {
            return configMap.getMetadata();
        }
    }

    @Singleton
    static class ConfigMapWatchService extends WatchService<V1ConfigMap, V1ConfigMapList, ConfigMapKey> {
        private final CoreV1Api coreApi;
        private final String namespace;

        @Inject
        public ConfigMapWatchService(final ApiClient apiClient,
                                     final ResourceCache<ConfigMapKey, V1ConfigMap> configMapCache,
                                     final CoreV1Api coreApi,
                                     @Namespace final String namespace) {
            super(apiClient, configMapCache);

            this.coreApi = coreApi;
            this.namespace = namespace;
        }

        @Override
        protected Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException {
            return coreApi.listNamespacedConfigMapCall(namespace, null, null, continueToken, null, "operator=instaclustr-cassandra-operator", null, resourceVersion, null, watch, null, null);
        }

        @Override
        protected Collection<? extends V1ConfigMap> resourceListItems(final V1ConfigMapList configMapList) {
            return configMapList.getItems();
        }

        @Override
        protected V1ListMeta resourceListMetadata(final V1ConfigMapList configMapList) {
            return configMapList.getMetadata();
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<ResourceCache<ConfigMapKey, V1ConfigMap>>() {}).to(ConfigMapCache.class);

        Multibinder.newSetBinder(binder(), Service.class).addBinding().to(ConfigMapWatchService.class);

        install(new FactoryModuleBuilder().build(ConfigMapWatchEvent.Factory.class));
    }
}
