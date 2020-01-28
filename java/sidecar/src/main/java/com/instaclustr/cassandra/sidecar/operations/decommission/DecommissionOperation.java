package com.instaclustr.cassandra.sidecar.operations.decommission;

import javax.inject.Inject;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.FunctionWithEx;
import com.instaclustr.operations.Operation;
import jmx.org.apache.cassandra.service.CassandraJMXService;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class DecommissionOperation extends Operation<DecommissionOperationRequest> {
    private final CassandraJMXService cassandraJMXService;

    @Inject
    public DecommissionOperation(final CassandraJMXService cassandraJMXService,
                                 @Assisted final DecommissionOperationRequest request) {
        super(request);

        this.cassandraJMXService = cassandraJMXService;
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
        cassandraJMXService = null;
    }

    @Override
    protected void run0() throws Exception {
        assert cassandraJMXService != null;
        cassandraJMXService.doWithStorageServiceMBean(new FunctionWithEx<StorageServiceMBean, Void>() {
            @Override
            public Void apply(final StorageServiceMBean object) throws Exception {
                object.decommission();
                return null;
            }
        });
    }
}
