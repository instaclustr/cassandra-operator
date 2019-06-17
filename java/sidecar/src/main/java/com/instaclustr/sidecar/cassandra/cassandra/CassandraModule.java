package com.instaclustr.sidecar.cassandra.cassandra;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnectorFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.sidecar.cassandra.picocli.CassandraSidecarCLIOptions;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CassandraModule extends AbstractModule {

    @Singleton
    @Provides
    StorageServiceMBean provideStorageServiceMBean(final MBeanServerConnection mBeanServerConnection) {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }

    @Singleton
    @Provides
    MBeanServerConnection provideMBeanServerConnection(final CassandraSidecarCLIOptions cliOptions) throws Exception {
        return JMXConnectorFactory.connect(cliOptions.jmxServiceURL).getMBeanServerConnection();
    }
}
