package com.instaclustr.cassandra.sidecar.operations.upgradesstables;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.sidecar.exception.OperationFailureException;
import com.instaclustr.sidecar.operations.Operation;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeSSTablesOperation extends Operation<UpgradeSSTablesOperationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(UpgradeSSTablesOperation.class);

    private final StorageServiceMBean storageServiceMBean;

    @Inject
    public UpgradeSSTablesOperation(final StorageServiceMBean storageServiceMBean,
                                    @Assisted final UpgradeSSTablesOperationRequest request) {
        super(request);

        this.storageServiceMBean = storageServiceMBean;
    }

    @Override
    protected void run0() throws Exception {

        final int concurrentCompactors = storageServiceMBean.getConcurrentCompactors();

        if (request.jobs > concurrentCompactors) {
            logger.info(String.format("jobs (%d) is bigger than configured concurrent_compactors (%d) on this host, using at most %d threads",
                                      request.jobs,
                                      concurrentCompactors,
                                      concurrentCompactors));
        }

        final int result = storageServiceMBean.upgradeSSTables(request.keyspace,
                                                               !request.includeAllSSTables,
                                                               request.jobs,
                                                               request.tables == null ? new String[]{} : request.tables.toArray(new String[0]));

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
