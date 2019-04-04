package com.instaclustr.cassandra.operator.watch;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.BackupWatchEvent;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.BackupList;
import com.instaclustr.cassandra.operator.model.key.BackupKey;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.k8s.watch.WatchService;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

public class BackupWatchModule extends AbstractModule {
    @Singleton
    static class BackupCache extends ResourceCache<BackupKey, Backup> {
        @Inject
        public BackupCache(final EventBus eventBus, final BackupWatchEvent.Factory eventFactory) {
            super(eventBus, eventFactory);
        }

        @Override
        protected BackupKey resourceKey(final Backup backup) {
            return BackupKey.forBackup(backup);
        }

        @Override
        protected V1ObjectMeta resourceMetadata(final Backup backup) {
            return backup.getMetadata();
        }
    }

    @Singleton
    static class BackupWatchService extends WatchService<Backup, BackupList, BackupKey> {
        private final CustomObjectsApi customObjectsApi;
        private final String namespace;

        @Inject
        public BackupWatchService(final ApiClient apiClient,
                                  final ResourceCache<BackupKey, Backup> backupCache,
                                  final CustomObjectsApi customObjectsApi,
                                  @Namespace final String namespace) {
            super(apiClient, backupCache);

            this.customObjectsApi = customObjectsApi;
            this.namespace = namespace;
        }

        @Override
        protected Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException {
            return customObjectsApi.listNamespacedCustomObjectCall("stable.instaclustr.com", "v1", namespace, "cassandra-backups", null, null, resourceVersion, watch, null, null);
        }

        @Override
        protected Collection<? extends Backup> resourceListItems(final BackupList backupList) {
            return backupList.getItems();
        }

        @Override
        protected V1ListMeta resourceListMetadata(final BackupList backupList) {
            return backupList.getMetadata();
        }
    }

    protected void configure() {
        bind(new TypeLiteral<ResourceCache<BackupKey, Backup>>() {}).to(BackupCache.class);

        Multibinder.newSetBinder(binder(), Service.class).addBinding().to(BackupWatchService.class);

        install(new FactoryModuleBuilder().build(BackupWatchEvent.Factory.class));
    }
}
