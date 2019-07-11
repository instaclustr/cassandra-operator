package com.instaclustr.cassandra.sidecar.operations.cleanup;

import javax.inject.Inject;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.sidecar.exception.OperationFailureException;
import com.instaclustr.sidecar.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public class CleanupOperation extends Operation<CleanupOperationRequest> {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public CleanupOperation(final StorageServiceMBean storageServiceMBean,
                            @Assisted final CleanupOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    @Override
    protected void run0() throws Exception {
        int result = storageServiceMBean.forceKeyspaceCleanup(request.jobs, request.keyspace, request.tables == null ? new String[]{} : request.tables.toArray(new String[]{}));

        switch (result) {
            case 1:
                throw new OperationFailureException("Aborted cleaning up at least one table in keyspace " + request.keyspace + ", check server logs for more information.");
            case 2:
                throw new OperationFailureException("Failed marking some sstables compacting in keyspace " + request.keyspace + ", check server logs for more information");
        }
    }
}
