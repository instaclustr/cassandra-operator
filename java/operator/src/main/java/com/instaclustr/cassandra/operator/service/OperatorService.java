package com.instaclustr.cassandra.operator.service;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.controller.DataCenterControllerFactory;
import com.instaclustr.cassandra.operator.event.*;
import com.instaclustr.cassandra.operator.jmx.CassandraConnection;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class OperatorService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(OperatorService.class);

    private static final DataCenterKey POISON = new DataCenterKey(null, null);

    private final DataCenterControllerFactory dataCenterControllerFactory;
    private final Map<DataCenterKey, DataCenter> dataCenterCache;

    private final BlockingQueue<DataCenterKey> dataCenterQueue = new LinkedBlockingQueue<>();

    @Inject
    public OperatorService(final DataCenterControllerFactory dataCenterControllerFactory, final Map<DataCenterKey, DataCenter> dataCenterCache) {
        this.dataCenterControllerFactory = dataCenterControllerFactory;
        this.dataCenterCache = dataCenterCache;
    }

    @Subscribe
    void clusterEvent(final ClusterWatchEvent event) {
        logger.trace("Received ClusterWatchEvent {}.", event);
        // TODO: map the Cluster object to one or more DC objects, then post a message on the queue for them - When we support cluster objects
    }

    @Subscribe
    void handleDataCenterEvent(final DataCenterWatchEvent event) {
        logger.trace("Received DataCenterWatchEvent {}.", event);
        dataCenterQueue.add(DataCenterKey.forDataCenter(event.dataCenter));
    }

    @Subscribe
    void handleSecretEvent(final SecretWatchEvent event) {
        logger.trace("Received SecretWatchEvent {}.", event);
        // TODO: handle updated/deleted secrets - currently we don't care about this
    }

    @Subscribe
    void handleStatefulSetEvent(final StatefulSetWatchEvent event) {
        logger.trace("Received StatefulSetWatchEvent {}.", event);

        // Trigger a dc reconciliation event if changes to the stateful set has finished.
        if(event.statefulSet.getStatus().getReplicas().equals(event.statefulSet.getStatus().getReadyReplicas()) && event.statefulSet.getStatus().getCurrentReplicas().equals(event.statefulSet.getStatus().getReplicas())) {
            String datacenterName = event.statefulSet.getMetadata().getLabels().get("cassandra-datacenter");
            if(datacenterName != null)
                dataCenterQueue.add(new DataCenterKey(event.statefulSet.getMetadata().getNamespace(), datacenterName));
        }
    }

    private static final EnumSet<CassandraConnection.Status.OperationMode> RECONCILE_OPERATION_MODES = EnumSet.of(
            // Reconcile when nodes switch to NORMAL. There may be pending scale operations that were
            // waiting for a healthy cluster.
            CassandraConnection.Status.OperationMode.NORMAL,

            // Reconcile when nodes have finished decommissioning. This will resume the StatefulSet
            // reconciliation.
            CassandraConnection.Status.OperationMode.DECOMMISSIONED
    );

    @Subscribe
    void handleCassandraNodeOperationModeChangedEvent(final CassandraNodeStatusChangedEvent event) {
        logger.trace("Received CassandraNodeStatusChangedEvent {}.", event);

        if (event.previousStatus.operationMode == event.currentStatus.operationMode)
            return;

        if (!RECONCILE_OPERATION_MODES.contains(event.currentStatus.operationMode))
            return;

        dataCenterQueue.add(event.dataCenterKey);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            final DataCenterKey dataCenterKey = dataCenterQueue.take();
            if (dataCenterKey == POISON)
                return;

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterName = MDC.putCloseable("DataCenter", dataCenterKey.name);
                 @SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterNamespace = MDC.putCloseable("Namespace", dataCenterKey.namespace)) {

                final DataCenter dataCenter = dataCenterCache.get(dataCenterKey);

                // data center deleted
                if (dataCenter == null) {
                    logger.info("Deleting Data Center.", dataCenterKey);
                    dataCenterControllerFactory.deletionControllerForDataCenter(dataCenterKey).deleteDataCenter();

                    continue;
                }

                // data center created or modified
                try {
                    logger.info("Reconciling Data Center.");
                    dataCenterControllerFactory.reconciliationControllerForDataCenter(dataCenter).reconcileDataCenter();

                } catch (final Exception e) {
                    logger.warn("Failed to reconcile Data Center. This will be an exception in the future.", e);
                }
            }
        }
    }

    @Override
    protected void triggerShutdown() {
        dataCenterQueue.add(POISON);
    }
}
