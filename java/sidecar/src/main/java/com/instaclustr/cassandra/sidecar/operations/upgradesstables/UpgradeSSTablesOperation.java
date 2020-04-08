package com.instaclustr.cassandra.sidecar.operations.upgradesstables;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.FunctionWithEx;
import com.instaclustr.operations.Operation;
import com.instaclustr.operations.OperationFailureException;
import jmx.org.apache.cassandra.service.CassandraJMXService;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeSSTablesOperation extends Operation<UpgradeSSTablesOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(UpgradeSSTablesOperation.class);

    private final CassandraJMXService cassandraJMXService;

    @Inject
    public UpgradeSSTablesOperation(final CassandraJMXService cassandraJMXService,
                                    @Assisted final UpgradeSSTablesOperationRequest request) {
        super(request);

        this.cassandraJMXService = cassandraJMXService;
    }

    // this constructor is not meant to be instantiated manually
    // and it fulfills the purpose of deserialisation from JSON string to an Operation object, currently just for testing purposes
    @JsonCreator
    private UpgradeSSTablesOperation(@JsonProperty("id") final UUID id,
                                     @JsonProperty("creationTime") final Instant creationTime,
                                     @JsonProperty("state") final State state,
                                     @JsonProperty("failureCause") final Throwable failureCause,
                                     @JsonProperty("progress") final float progress,
                                     @JsonProperty("startTime") final Instant startTime,
                                     @JsonProperty("keyspace") final String keyspace,
                                     @JsonProperty("tables") final Set<String> tables,
                                     @JsonProperty("includeAllSStables") final boolean includeAllSStables,
                                     @JsonProperty("jobs") final int jobs) {
        super(id, creationTime, state, failureCause, progress, startTime,
              new UpgradeSSTablesOperationRequest(keyspace, tables, includeAllSStables, jobs));
        cassandraJMXService = null;
    }

    @Override
    protected void run0() throws Exception {
        assert cassandraJMXService != null;

        final Integer concurrentCompactors = cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, Integer>() {
            @Override
            public Integer apply(final StorageServiceMBean object) {
                return object.getConcurrentCompactors();
            }
        });

        if (request.jobs > concurrentCompactors) {
            logger.info(String.format("jobs (%d) is bigger than configured concurrent_compactors (%d) on this host, using at most %d threads",
                                      request.jobs,
                                      concurrentCompactors,
                                      concurrentCompactors));
        }

        final Integer result = cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, Integer>() {
            @Override
            public Integer apply(final StorageServiceMBean object) throws Exception {
                return object.upgradeSSTables(request.keyspace,
                                              !request.includeAllSStables,
                                              request.jobs,
                                              request.tables == null ? new String[]{} : request.tables.toArray(new String[0]));
            }
        });

        switch (result) {
            case 1:
                throw new OperationFailureException("Aborted upgrading sstables for at least one table in keyspace " + request.keyspace
                                                        + ", check server logs for more information.");
            case 2:
                throw new OperationFailureException("Failed marking some sstables compacting in keyspace " + request.keyspace +
                                                        ", check server logs for more information\"");
        }
    }
}
