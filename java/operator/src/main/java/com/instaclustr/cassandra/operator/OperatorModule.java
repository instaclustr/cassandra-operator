package com.instaclustr.cassandra.operator;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.controller.DataCenterControllerFactory;
import com.instaclustr.cassandra.operator.modules.*;
import com.instaclustr.cassandra.operator.service.BackupControllerService;
import com.instaclustr.cassandra.operator.service.CassandraHealthCheckService;
import com.instaclustr.cassandra.operator.service.OperatorService;
import com.instaclustr.cassandra.operator.sidecar.SidecarClientModule;

public class OperatorModule extends AbstractModule {

    @Override
    protected void configure() {
        final Multibinder<Service> serviceMultibinder = Multibinder.newSetBinder(binder(), Service.class);

        serviceMultibinder.addBinding().to(OperatorService.class);
        serviceMultibinder.addBinding().to(CassandraHealthCheckService.class);
        serviceMultibinder.addBinding().to(BackupControllerService.class);

        install(new SidecarClientModule());

        install(new FactoryModuleBuilder().build(DataCenterControllerFactory.class));

        install(new ClusterWatchModule());
        install(new DataCenterWatchModule());
        install(new StatefulSetWatchModule());
        install(new ConfigMapWatchModule());
        install(new BackupWatchModule());
    }
}
