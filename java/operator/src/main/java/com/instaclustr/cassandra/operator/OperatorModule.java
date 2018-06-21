package com.instaclustr.cassandra.operator;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.modules.*;
import com.instaclustr.cassandra.operator.service.BackupControllerService;
import com.instaclustr.cassandra.operator.service.CassandraHealthCheckService;
import com.instaclustr.cassandra.operator.service.ControllerService;
import com.instaclustr.cassandra.operator.service.GarbageCollectorService;

public class OperatorModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(ControllerService.class);
        serviceMultibinder.addBinding().to(GarbageCollectorService.class);
        serviceMultibinder.addBinding().to(CassandraHealthCheckService.class);
        serviceMultibinder.addBinding().to(BackupControllerService.class);

        install(new ClusterWatchModule());
        install(new DataCenterWatchModule());
        install(new StatefulSetWatchModule());
        install(new ConfigMapWatchModule());
        install(new BackupWatchModule());
    }
}
