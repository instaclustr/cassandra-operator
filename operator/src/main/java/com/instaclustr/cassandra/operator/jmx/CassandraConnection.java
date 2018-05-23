package com.instaclustr.cassandra.operator.jmx;

import com.google.common.base.MoreObjects;

import javax.management.*;
import javax.management.remote.JMXConnector;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;


public class CassandraConnection implements Closeable {
    private static final ObjectName STORAGE_SERVICE_OBJECT_NAME;

    static {
        try {
            STORAGE_SERVICE_OBJECT_NAME = ObjectName.getInstance("org.apache.cassandra.db:type=StorageService");

        } catch (final MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
    }

    private final JMXConnector jmxConnector;
    private final StorageServiceMBean storageService;


    public interface StorageServiceMBean {
        void decommission();

        List<String> getLiveNodes();
        List<String> getUnreachableNodes();

        String getReleaseVersion();
        String getSchemaVersion();

        String getOperationMode();
    }

    CassandraConnection(final JMXConnector jmxConnector) throws IOException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        this.jmxConnector = jmxConnector;

        final MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();

        final String releaseVersion = (String) connection.getAttribute(STORAGE_SERVICE_OBJECT_NAME, "ReleaseVersion");


        this.storageService = JMX.newMBeanProxy(connection, STORAGE_SERVICE_OBJECT_NAME, StorageServiceMBean.class);
    }




    @Override
    public void close() throws IOException {
        jmxConnector.close();
    }

    public static final class Status {
        public Status(final OperationMode operationMode) {
            this.operationMode = operationMode;
        }

        public enum OperationMode {
            STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
        }

        public final OperationMode operationMode;

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Status status = (Status) o;
            return operationMode == status.operationMode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationMode);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("operationMode", operationMode)
                    .toString();
        }
    }

    public Status status() {
        final Status.OperationMode operationMode = Status.OperationMode.valueOf(storageService.getOperationMode());

        return new Status(operationMode);
    }

    public void decommission() {
        storageService.decommission();
    }


}
