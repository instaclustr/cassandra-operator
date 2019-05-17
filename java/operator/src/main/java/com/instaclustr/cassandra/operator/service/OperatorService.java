package com.instaclustr.cassandra.operator.service;

import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.controller.DataCenterControllerFactory;
import com.instaclustr.cassandra.operator.event.*;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.cassandra.operator.event.DataCenterWatchEvent;
import com.instaclustr.cassandra.sidecar.model.Status;
import com.instaclustr.guava.EventBusSubscriber;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.models.V1beta2StatefulSetStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport.putNamespacedName;

@EventBusSubscriber
public class OperatorService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(OperatorService.class);

    private static final DataCenterKey POISON = new DataCenterKey(null, null);

    private final DataCenterControllerFactory dataCenterControllerFactory;
    private final ResourceCache<DataCenterKey, DataCenter> dataCenterCache;

    private final BlockingQueue<DataCenterKey> dataCenterQueue = new LinkedBlockingQueue<>();

    @Inject
    public OperatorService(final DataCenterControllerFactory dataCenterControllerFactory, final ResourceCache<DataCenterKey, DataCenter> dataCenterCache) {
        this.dataCenterControllerFactory = dataCenterControllerFactory;
        this.dataCenterCache = dataCenterCache;
    }

    @Subscribe
    void clusterEvent(final ClusterWatchEvent event) {
        logger.debug("Received ClusterWatchEvent {}.", event);
        // TODO: map the Cluster object to one or more DC objects, then post a message on the queue for them - When we support cluster objects
    }

    @Subscribe
    void handleDataCenterEvent(final DataCenterWatchEvent event) {
        logger.debug("Received DataCenterWatchEvent {}.", event);
        dataCenterQueue.add(DataCenterKey.forDataCenter(event.dataCenter));
    }

    @Subscribe
    void handleSecretEvent(final SecretWatchEvent event) {
        logger.debug("Received SecretWatchEvent {}.", event);
        // TODO: handle updated/deleted secrets - currently we don't care about this
    }

    @Subscribe
    void handleStatefulSetEvent(final StatefulSetWatchEvent event) {
        logger.debug("Received StatefulSetWatchEvent {}.", event);

        if (event instanceof StatefulSetWatchEvent.Modified) {
            final String dataCenterName = event.statefulSet.getMetadata().getLabels().get(OperatorLabels.DATACENTER);
            if (dataCenterName != null) {
                dataCenterQueue.add(new DataCenterKey(dataCenterName, event.statefulSet.getMetadata().getNamespace()));
            }
        }
    }

    private static final Set<Status.OperationMode> RECONCILE_OPERATION_MODES = Sets.immutableEnumSet(
            // Reconcile when nodes switch to NORMAL. There may be pending scale operations that were
            // waiting for a healthy cluster.
            Status.OperationMode.NORMAL,

            // Reconcile when nodes have finished decommissioning. This will resume the StatefulSet
            // reconciliation.
            Status.OperationMode.DECOMMISSIONED
    );

    @Subscribe
    void handleCassandraNodeOperationModeChangedEvent(final CassandraNodeOperationModeChangedEvent event) {
        logger.debug("Received CassandraNodeOperationModeChangedEvent {}.", event);

        if (!RECONCILE_OPERATION_MODES.contains(event.currentMode))
            return;

        dataCenterQueue.add(event.dataCenterKey);
    }

    @Override
    protected void run() throws Exception {
        while (true) {
            final DataCenterKey dataCenterKey = dataCenterQueue.take();
            if (dataCenterKey == POISON)
                return;

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterMDC = putNamespacedName("DataCenter", dataCenterKey)) {
                final DataCenter dataCenter = dataCenterCache.get(dataCenterKey);

                // data center was removed from cache, delete
                if (dataCenter == null) {
                    try {
                        dataCenterControllerFactory.deletionControllerForDataCenter(dataCenterKey).deleteDataCenter();

                    } catch (final Exception e) {
                        logger.warn("Failed to delete Data Center.", e);
                    }

                    continue;
                }

                // data center created or modified
                try {
                    dataCenterControllerFactory.reconciliationControllerForDataCenter(dataCenter).reconcileDataCenter();

                } catch (final Exception e) {
                    logger.warn("Failed to reconcile Data Center.", e);
                }
            }
        }
    }

    @Override
    protected void triggerShutdown() {
        dataCenterQueue.add(POISON);
    }
}
