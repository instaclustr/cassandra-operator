package com.instaclustr.cassandra.sidecar.cassandra;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CassandraModule extends AbstractModule {
    private final JMXServiceURL jmxServiceURL;

    public CassandraModule(final JMXServiceURL jmxServiceURL) {
        this.jmxServiceURL = jmxServiceURL;
    }

    @Singleton
    @Provides
    StorageServiceMBean provideStorageServiceMBean(final MBeanServerConnection mBeanServerConnection) {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }

    @Singleton()
    @Provides
    MBeanServerConnection provideMBeanServerConnection() throws Exception {
        return JMXConnectorFactory.connect(jmxServiceURL).getMBeanServerConnection();
    }
}
