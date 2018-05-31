package com.instaclustr.cassandra.operator;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.instaclustr.cassandra.operator.modules.ClusterWatchModule;
import com.instaclustr.cassandra.operator.modules.ConfigMapWatchModule;
import com.instaclustr.cassandra.operator.modules.DataCenterWatchModule;
import com.instaclustr.cassandra.operator.modules.StatefulSetWatchModule;
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

        install(new ClusterWatchModule());
        install(new DataCenterWatchModule());
        install(new StatefulSetWatchModule());
        install(new ConfigMapWatchModule());
    }
}
