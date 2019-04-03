package com.instaclustr.cassandra.sidecar.cassandra;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.management.JMX;
import javax.management.MBeanServerConnection;

public class CassandraModule extends AbstractModule {
    private final MBeanServerConnection mBeanServerConnection;

    public CassandraModule(final MBeanServerConnection mBeanServerConnection) {
        this.mBeanServerConnection = mBeanServerConnection;
    }

    @Provides
    @Singleton
    StorageServiceMBean storageServiceMBeanProvider() {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }
}
