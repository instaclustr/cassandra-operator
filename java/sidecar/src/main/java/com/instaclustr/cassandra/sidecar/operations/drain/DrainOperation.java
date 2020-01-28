package com.instaclustr.cassandra.sidecar.operations.drain;

import static com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState.DRAINED;
import static com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState.DRAINING;
import static com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState.NORMAL;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status;
import com.instaclustr.operations.FunctionWithEx;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationFailureException;
import jmx.org.apache.cassandra.service.CassandraJMXService;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrainOperation extends Operation<DrainOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(DrainOperation.class);

    private final CassandraStatusService cassandraStatusService;
    private final CassandraJMXService cassandraJMXService;

    @Inject
    public DrainOperation(final CassandraStatusService cassandraStatusService,
                          final CassandraJMXService cassandraJMXService,
                          @Assisted final DrainOperationRequest request) {
        super(request);
        this.cassandraStatusService = cassandraStatusService;
        this.cassandraJMXService = cassandraJMXService;
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
        this.cassandraStatusService = null;
        this.cassandraJMXService = null;
    }

    @Override
    protected void run0() throws Exception {
        assert cassandraStatusService != null;
        assert cassandraJMXService != null;

        final Status status = cassandraStatusService.getStatus();

        if (status.getNodeState() == DRAINED || status.getNodeState() == DRAINING) {
            return;
        }

        if (status.getNodeState() != NORMAL) {
            throw new OperationFailureException(format("Cassandra node is not in state NORMAL to drain it. It is in the state \"%s\"", status.getNodeState()));
        }

        cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, Void>() {
            @Override
            public Void apply(final StorageServiceMBean ssMBean) throws Exception {

                ssMBean.drain();

                logger.info("Draining of Cassandra node started.");

                await().atMost(10, TimeUnit.MINUTES).until(ssMBean::isDrained);

                logger.info("Draining of Cassandra node finished.");

                return null;
            }
        });
    }
}
