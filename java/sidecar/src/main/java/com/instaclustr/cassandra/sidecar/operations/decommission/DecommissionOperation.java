package com.instaclustr.cassandra.sidecar.operations.decommission;

import javax.inject.Inject;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class DecommissionOperation extends Operation<DecommissionOperationRequest> {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public DecommissionOperation(final StorageServiceMBean storageServiceMBean,
                                 @Assisted final DecommissionOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private DecommissionOperation(@JsonProperty("id") final UUID id,
                                  @JsonProperty("creationTime") final Instant creationTime,
                                  @JsonProperty("state") final State state,
                                  @JsonProperty("failureCause") final Throwable failureCause,
                                  @JsonProperty("progress") final float progress,
                                  @JsonProperty("startTime") final Instant startTime) {
        super(id, creationTime, state, failureCause, progress, startTime, new DecommissionOperationRequest());
        storageServiceMBean = null;
    }

    @Override
    protected void run0() throws Exception {
        storageServiceMBean.decommission();
    }
}
