package com.instaclustr.cassandra.sidecar.operations.flush;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.FunctionWithEx;
import com.instaclustr.operations.Operation;
import jmx.org.apache.cassandra.service.CassandraJMXService;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlushOperation extends Operation<FlushOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(FlushOperation.class);

    private final CassandraJMXService cassandraJMXService;

    @Inject
    protected FlushOperation(final CassandraJMXService cassandraJMXService,
                             @Assisted final FlushOperationRequest request) {
        super(request);

        this.cassandraJMXService = cassandraJMXService;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private FlushOperation(@JsonProperty("id") final UUID id,
                           @JsonProperty("creationTime") final Instant creationTime,
                           @JsonProperty("state") final State state,
                           @JsonProperty("failureCause") final Throwable failureCause,
                           @JsonProperty("progress") final float progress,
                           @JsonProperty("startTime") final Instant startTime,
                           @JsonProperty("keyspace") final String keyspace,
                           @JsonProperty("tables") final Set<String> tables) {
        super(id, creationTime, state, failureCause, progress, startTime, new FlushOperationRequest(keyspace, tables));
        cassandraJMXService = null;
    }

    @Override
    protected void run0() throws Exception {
        assert cassandraJMXService != null;

        cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, Object>() {
            @Override
            public Object apply(final StorageServiceMBean object) throws Exception {

                String[] tables = request.tables == null ? new String[]{} : request.tables.toArray(new String[]{});

                object.forceKeyspaceFlush(request.keyspace,
                                          tables);

                if (tables.length == 0) {
                    logger.info(String.format("Flushed keyspace %s on all tables", request.keyspace));
                } else {
                    logger.info(String.format("Flushed keyspace %s on tables %s", request.keyspace, Arrays.asList(tables)));
                }

                return null;
            }
        });
    }
}
