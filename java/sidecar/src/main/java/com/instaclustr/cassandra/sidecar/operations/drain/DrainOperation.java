package com.instaclustr.cassandra.sidecar.operations.drain;

import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrainOperation extends Operation<DrainOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(DrainOperation.class);

    private final StorageServiceMBean storageServiceMBean;

    @Inject
    protected DrainOperation(final StorageServiceMBean storageServiceMBean,
                             @Assisted final DrainOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private DrainOperation(@JsonProperty("id") final UUID id,
                           @JsonProperty("creationTime") final Instant creationTime,
                           @JsonProperty("state") final State state,
                           @JsonProperty("failureCause") final Throwable failureCause,
                           @JsonProperty("progress") final float progress,
                           @JsonProperty("startTime") final Instant startTime) {
        super(id, creationTime, state, failureCause, progress, startTime, new DrainOperationRequest());
        this.storageServiceMBean = null;
    }

    @Override
    protected void run0() throws Exception {
        assert storageServiceMBean != null;

        if (!storageServiceMBean.isDrained()) {
            if (!storageServiceMBean.isDraining()) {
                storageServiceMBean.drain();
                await().forever().until(storageServiceMBean::isDrained);
            } else {
                logger.info(format("Draining operation %s has not run because node is already draining.", this.id));
            }
        } else {
            logger.info(format("Draining operation %s has not run because node is already drained.", this.id));
        }
    }
}
