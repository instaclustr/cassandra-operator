package com.instaclustr.cassandra.sidecar.operation.task;

import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;
import com.instaclustr.cassandra.sidecar.model.result.DecommissionResult;
import com.instaclustr.cassandra.sidecar.operation.OperationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecommissionTask extends OperationTask<DecommissionOperation, DecommissionResult> {

    private static final Logger logger = LoggerFactory.getLogger(DecommissionTask.class);

    public DecommissionTask(final DecommissionOperation operation) {
        super(operation, new DecommissionResult(operation.getId()));
    }

    @Override
    protected void executeTask(final DecommissionOperation operation, final DecommissionResult decommissionResult) throws Exception {

        logger.info("Decommission task {} started.", operation.getId());

        try {
            storageServiceMBean.decommission();
        } finally {
            logger.info("Decommission task {} stopped.", operation.getId());
        }
    }
}
