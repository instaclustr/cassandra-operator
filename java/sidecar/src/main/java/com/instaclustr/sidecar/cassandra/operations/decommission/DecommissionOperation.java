package com.instaclustr.sidecar.cassandra.operations.decommission;

import javax.inject.Inject;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

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
