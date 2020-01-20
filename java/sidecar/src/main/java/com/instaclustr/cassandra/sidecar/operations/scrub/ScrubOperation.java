package com.instaclustr.cassandra.sidecar.operations.scrub;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationFailureException;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrubOperation extends Operation<ScrubOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(ScrubOperation.class);

    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public ScrubOperation(final StorageServiceMBean storageServiceMBean,
                          @Assisted final ScrubOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private ScrubOperation(@JsonProperty("id") final UUID id,
                           @JsonProperty("creationTime") final Instant creationTime,
                           @JsonProperty("state") final State state,
                           @JsonProperty("failureCause") final Throwable failureCause,
                           @JsonProperty("progress") final float progress,
                           @JsonProperty("startTime") final Instant startTime,
                           @JsonProperty("disableSnapshot") final boolean disableSnapshot,
                           @JsonProperty("skipCorrupted") final boolean skipCorrupted,
                           @JsonProperty("noValidate") final boolean noValidate,
                           @JsonProperty("reinsertOverflowedTTL") final boolean reinsertOverflowedTTL,
                           @JsonProperty("jobs") final int jobs,
                           @JsonProperty("keyspace") final String keyspace,
                           @JsonProperty("tables") final Set<String> tables) {
        super(id, creationTime, state, failureCause, progress, startTime,
              new ScrubOperationRequest(disableSnapshot, skipCorrupted, noValidate, reinsertOverflowedTTL, jobs, keyspace, tables));
        storageServiceMBean = null;
    }

    @Override
    protected void run0() throws Exception {

        assert storageServiceMBean != null;
        final int concurrentCompactors = storageServiceMBean.getConcurrentCompactors();

        if (request.jobs > concurrentCompactors) {
            logger.info(String.format("jobs (%d) is bigger than configured concurrent_compactors (%d) on this host, using at most %d threads",
                                      request.jobs,
                                      concurrentCompactors,
                                      concurrentCompactors));
        }

        final int result = storageServiceMBean.scrub(request.disableSnapshot,
                                                     request.skipCorrupted,
                                                     !request.noValidate,
                                                     request.reinsertOverflowedTTL,
                                                     request.jobs,
                                                     request.keyspace,
                                                     request.tables == null ? new String[]{} : request.tables.toArray(new String[0]));

        switch (result) {
            case 1:
                throw new OperationFailureException("Aborted scrubbing at least one table in keyspace " + request.keyspace
                                                            + ", check server logs for more information.");
            case 2:
                throw new OperationFailureException("Failed marking some sstables compacting in keyspace " + request.keyspace
                                                            + ", check server logs for more information");
        }
    }
}
