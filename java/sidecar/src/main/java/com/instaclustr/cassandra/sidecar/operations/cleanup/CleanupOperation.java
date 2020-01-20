package com.instaclustr.cassandra.sidecar.operations.cleanup;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationFailureException;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CleanupOperation extends Operation<CleanupOperationRequest> {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public CleanupOperation(final StorageServiceMBean storageServiceMBean,
                            @Assisted final CleanupOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private CleanupOperation(@JsonProperty("id") final UUID id,
                             @JsonProperty("creationTime") final Instant creationTime,
                             @JsonProperty("state") final State state,
                             @JsonProperty("failureCause") final Throwable failureCause,
                             @JsonProperty("progress") final float progress,
                             @JsonProperty("startTime") final Instant startTime,
                             @JsonProperty("keyspace") final String keyspace,
                             @JsonProperty("tables") final Set<String> tables,
                             @JsonProperty("jobs") final int jobs) {
        super(id, creationTime, state, failureCause, progress, startTime, new CleanupOperationRequest(keyspace, tables, jobs));
        storageServiceMBean = null;
    }

    @Override
    protected void run0() throws Exception {
        assert storageServiceMBean != null;
        int result = storageServiceMBean.forceKeyspaceCleanup(request.jobs, request.keyspace, request.tables == null ? new String[]{} : request.tables.toArray(new String[]{}));

        switch (result) {
            case 1:
                throw new OperationFailureException("Aborted cleaning up at least one table in keyspace " + request.keyspace + ", check server logs for more information.");
            case 2:
                throw new OperationFailureException("Failed marking some sstables compacting in keyspace " + request.keyspace + ", check server logs for more information");
        }
    }
}
