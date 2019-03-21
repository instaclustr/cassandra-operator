package com.instaclustr.cassandra.operator.watch;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.DataCenterWatchEvent;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.DataCenterList;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.k8s.watch.*;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

public class DataCenterWatchModule extends AbstractModule {
    @Singleton
    static class DataCenterCache extends ResourceCache<DataCenterKey, DataCenter> {
        @Inject
        public DataCenterCache(final EventBus eventBus, final DataCenterWatchEvent.Factory eventFactory) {
            super(eventBus, eventFactory);
        }

        @Override
        protected DataCenterKey resourceKey(final DataCenter dataCenter) {
            return DataCenterKey.forDataCenter(dataCenter);
        }

        @Override
        protected V1ObjectMeta resourceMetadata(final DataCenter dataCenter) {
            return dataCenter.getMetadata();
        }
    }

    @Singleton
    static class DataCenterWatchService extends WatchService<DataCenter, DataCenterList, DataCenterKey> {
        private final CustomObjectsApi customObjectsApi;
        private final String namespace;

        @Inject
        public DataCenterWatchService(final ApiClient apiClient,
                                      final ResourceCache<DataCenterKey, DataCenter> dataCenterCache,
                                      final CustomObjectsApi customObjectsApi,
                                      @Namespace final String namespace) {
            super(apiClient, dataCenterCache);

            this.customObjectsApi = customObjectsApi;
            this.namespace = namespace;
        }

        @Override
        protected Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException {
            return customObjectsApi.listNamespacedCustomObjectCall("stable.instaclustr.com", "v1", namespace, "cassandra-datacenters", null, null, resourceVersion, watch, null, null);
        }

        @Override
        protected Collection<? extends DataCenter> resourceListItems(final DataCenterList dataCenterList) {
            return dataCenterList.getItems();
        }

        @Override
        protected V1ListMeta resourceListMetadata(final DataCenterList dataCenterList) {
            return dataCenterList.getMetadata();
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<ResourceCache<DataCenterKey, DataCenter>>() {}).to(DataCenterCache.class);

        Multibinder.newSetBinder(binder(), Service.class).addBinding().to(DataCenterWatchService.class);

        install(new FactoryModuleBuilder().build(DataCenterWatchEvent.Factory.class));
    }
}
