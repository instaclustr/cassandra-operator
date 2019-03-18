package com.instaclustr.cassandra.operator.event;

import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.cassandra.sidecar.model.Status;
import io.kubernetes.client.models.V1Pod;

@SuppressWarnings("WeakerAccess")
public class CassandraNodeOperationModeChangedEvent {
    public final V1Pod pod;
    public final DataCenterKey dataCenterKey;
    public final Status.OperationMode previousMode;
    public final Status.OperationMode currentMode;

    public CassandraNodeOperationModeChangedEvent(final V1Pod pod, final DataCenterKey dataCenterKey, final Status.OperationMode previousMode, final Status.OperationMode currentMode) {
        this.pod = pod;
        this.dataCenterKey = dataCenterKey;
        this.previousMode = previousMode;
        this.currentMode = currentMode;
    }
}
