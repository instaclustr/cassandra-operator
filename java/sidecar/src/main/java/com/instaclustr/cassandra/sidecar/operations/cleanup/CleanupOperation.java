package com.instaclustr.cassandra.sidecar.operations.cleanup;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.sidecar.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.inject.Inject;

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
        storageServiceMBean.forceKeyspaceCleanup(request.keyspace, request.tables.toArray(new String[]{}));
    }
}
