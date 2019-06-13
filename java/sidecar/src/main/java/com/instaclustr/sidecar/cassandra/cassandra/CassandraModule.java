package com.instaclustr.sidecar.cassandra.cassandra;

import javax.management.JMX;
import javax.management.MBeanServerConnection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CassandraModule extends AbstractModule {

    private final MBeanServerConnection mBeanServerConnection;

    public CassandraModule(final MBeanServerConnection mBeanServerConnection) {
        this.mBeanServerConnection = mBeanServerConnection;
    }

    @Singleton
    @Provides
    StorageServiceMBean provideStorageServiceMBean() {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }
}
