package com.instaclustr.cassandra.sidecar.service;

import com.google.inject.Inject;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState;
import com.instaclustr.operations.FunctionWithEx;
import jmx.org.apache.cassandra.service.CassandraJMXService;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CassandraStatusService {

    private final CassandraJMXService cassandraJMXService;

    @Inject
    public CassandraStatusService(final CassandraJMXService cassandraJMXService) {
        this.cassandraJMXService = cassandraJMXService;
    }

    public Status getStatus() {
        final Status status = new Status();

        try {

            final String operationMode = cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, String>() {
                @Override
                public String apply(final StorageServiceMBean object) {
                    return object.getOperationMode();
                }
            });

            status.setNodeState(NodeState.valueOf(operationMode));
        } catch (Exception ex) {
            status.setException(ex);
        }

        return status;
    }

    public static class Status {

        public enum NodeState {
            STARTING, NORMAL, JOINING, LEAVING, DECOMMISSIONED, MOVING, DRAINING, DRAINED
        }

        private NodeState nodeState;

        private Exception exception;

        public NodeState getNodeState() {
            return nodeState;
        }

        public void setNodeState(NodeState nodeState) {
            this.nodeState = nodeState;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }
}
