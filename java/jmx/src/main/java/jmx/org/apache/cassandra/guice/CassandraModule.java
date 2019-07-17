package jmx.org.apache.cassandra.guice;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jmx.org.apache.cassandra.CassandraObjectNames;
import jmx.org.apache.cassandra.JMXConnectionInfo;
import jmx.org.apache.cassandra.JMXUtils;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CassandraModule extends AbstractModule {
    private final JMXConnectionInfo jmxConnectionInfo;

    public CassandraModule(final JMXConnectionInfo jmxConnectionInfo) {
        this.jmxConnectionInfo = jmxConnectionInfo;
    }

    @Singleton
    @Provides
    JMXConnectionInfo provideJmxConnectionInfo() {
        return this.jmxConnectionInfo;
    }

    @Singleton
    @Provides
    StorageServiceMBean provideStorageServiceMBean(final MBeanServerConnection mBeanServerConnection) {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }

    @Singleton
    @Provides
    MBeanServerConnection provideMBeanServerConnection(final JMXConnector jmxConnector) throws Exception {
        return JMXUtils.getMBeanServerConnection(jmxConnector);
    }

    @Provides
    @Singleton
    JMXConnector provideJmxConnectorFactory(final JMXConnectionInfo jmxConnectionInfo) throws Exception {
        return JMXUtils.getJmxConnector(jmxConnectionInfo);
    }
}
