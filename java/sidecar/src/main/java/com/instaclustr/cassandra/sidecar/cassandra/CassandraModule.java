package com.instaclustr.cassandra.sidecar.cassandra;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.cassandra.sidecar.operation.OperationExecutor;
import com.instaclustr.cassandra.sidecar.operation.DefaultOperationExecutor;
import com.instaclustr.cassandra.sidecar.operation.task.DefaultTaskFactory;
import com.instaclustr.cassandra.sidecar.operation.task.TaskFactory;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CassandraModule extends AbstractModule {
    private final MBeanServerConnection mBeanServerConnection;

    public CassandraModule(final MBeanServerConnection mBeanServerConnection) {
        this.mBeanServerConnection = mBeanServerConnection;
    }

    @Provides
    @Singleton
    public StorageServiceMBean storageServiceMBeanProvider() {
        return JMX.newMBeanProxy(mBeanServerConnection, CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, StorageServiceMBean.class);
    }

    @Provides
    @Singleton
    public OperationExecutor operationExecutor(StorageServiceMBean storageServiceMBean, ExecutorService executorService) {
        return new DefaultOperationExecutor(storageServiceMBean, executorService);
    }

    @Provides
    @Singleton
    public TaskFactory taskFactory() {
        return new DefaultTaskFactory();
    }

    @Provides
    @Singleton
    public ExecutorService operationsExecutorService() {
        return Executors.newFixedThreadPool(1);
    }
}
