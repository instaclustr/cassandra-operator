package com.instaclustr.cassandra.operator.event;

import com.instaclustr.cassandra.operator.jmx.CassandraConnection;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.models.V1Pod;

@SuppressWarnings("WeakerAccess")
public class CassandraNodeStatusChangedEvent {
    public final V1Pod pod;
    public final DataCenterKey dataCenterKey;
    public final CassandraConnection.Status previousStatus;
    public final CassandraConnection.Status currentStatus;

    public CassandraNodeStatusChangedEvent(final V1Pod pod, final DataCenterKey dataCenterKey, final CassandraConnection.Status previousStatus, final CassandraConnection.Status currentStatus) {
        this.pod = pod;
        this.dataCenterKey = dataCenterKey;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
    }
}
