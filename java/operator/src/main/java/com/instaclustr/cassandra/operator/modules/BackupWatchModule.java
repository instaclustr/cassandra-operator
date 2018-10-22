package com.instaclustr.cassandra.operator.modules;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.instaclustr.cassandra.operator.configuration.Namespace;
import com.instaclustr.cassandra.operator.event.BackupWatchEvent;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.BackupList;
import com.instaclustr.cassandra.operator.model.key.BackupKey;
import com.instaclustr.k8s.watch.ListCallProvider;
import com.instaclustr.k8s.watch.WatchService;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CustomObjectsApi;

import java.util.HashMap;
import java.util.Map;

public class BackupWatchModule extends AbstractModule {
    protected void configure() {
        bind(new TypeLiteral<Map<BackupKey, Backup>>() {}).toInstance(new HashMap<>());

        install(new FactoryModuleBuilder().build(BackupWatchEvent.Factory.class));
    }

    @ProvidesIntoSet
    Service provideBackupWatchService(final ApiClient apiClient, final CustomObjectsApi customObjectsApi,
                                      final EventBus eventBus, final BackupWatchEvent.Factory backupWatchEventFactory,
                                      final Map<BackupKey, Backup> cache,
                                      @Namespace final String namespace) {

        final ListCallProvider listCallProvider = (resourceVersion, watch) ->
                customObjectsApi.listNamespacedCustomObjectCall(
                        "stable.instaclustr.com", "v1", namespace, "cassandra-backups", null, null, resourceVersion, watch, null, null);

        return new WatchService<Backup, BackupList, BackupKey>(
                apiClient, eventBus,
                listCallProvider, backupWatchEventFactory,
                cache,
                BackupKey::forBackup,
                Backup::getMetadata,
                BackupList::getMetadata,
                BackupList::getItems
        ) {};
    }

}
