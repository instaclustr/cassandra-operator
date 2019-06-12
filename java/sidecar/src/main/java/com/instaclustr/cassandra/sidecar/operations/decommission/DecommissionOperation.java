package com.instaclustr.cassandra.sidecar.operations.decommission;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.sidecar.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

import javax.inject.Inject;

public class DecommissionOperation extends Operation<DecommissionOperationRequest> {
    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public DecommissionOperation(final StorageServiceMBean storageServiceMBean,
                                 @Assisted final DecommissionOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    @Override
    protected void run0() throws Exception {
        storageServiceMBean.decommission();
    }
}
