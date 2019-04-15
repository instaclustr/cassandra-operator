package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.instaclustr.cassandra.operator.event.CassandraNodeOperationModeChangedEvent;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.cassandra.operator.sidecar.SidecarClient;
import com.instaclustr.cassandra.operator.sidecar.SidecarClientFactory;
import com.instaclustr.cassandra.sidecar.model.Status;
import com.instaclustr.k8s.watch.ResourceCache;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport.putNamespacedName;


public class CassandraHealthCheckService extends AbstractScheduledService {
    private static final Logger logger = LoggerFactory.getLogger(CassandraHealthCheckService.class);

    private final K8sResourceUtils k8sResourceUtils;
    private final ResourceCache<DataCenterKey, DataCenter> dataCenterCache;
    private final SidecarClientFactory sidecarClientFactory;

    private final EventBus eventBus;

    private Cache<InetAddress, Status.OperationMode> cassandraNodeOperationModes = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();


    @Inject
    public CassandraHealthCheckService(final K8sResourceUtils k8sResourceUtils,
                                       final ResourceCache<DataCenterKey, DataCenter> dataCenterCache,
                                       final SidecarClientFactory sidecarClientFactory,
                                       final EventBus eventBus) {
        this.k8sResourceUtils = k8sResourceUtils;
        this.dataCenterCache = dataCenterCache;
        this.sidecarClientFactory = sidecarClientFactory;
        this.eventBus = eventBus;
    }


    @Override
    protected void runOneIteration() throws Exception {
        logger.debug("Checking health of Cassandra instances.");

        for (final Map.Entry<DataCenterKey, DataCenter> cacheEntry : dataCenterCache.entrySet()) {
             final DataCenterKey dataCenterKey = cacheEntry.getKey();

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterMDC = putNamespacedName("DataCenter", dataCenterKey)) {
                final String labelSelector = String.format("%s=%s", OperatorLabels.DATACENTER, dataCenterKey.name);

                final Iterable<V1Pod> pods = k8sResourceUtils.listNamespacedPods(dataCenterKey.namespace, "status.phase=Running", labelSelector);

                final Map<V1Pod, SidecarClient> podClients = Maps.toMap(pods, sidecarClientFactory::clientForPod);
                final Map<V1Pod, Future<Status>> podStatuses = ImmutableMap.copyOf(Maps.transformValues(podClients, SidecarClient::status));

                for (final Map.Entry<V1Pod, Future<Status>> entry : podStatuses.entrySet()) {
                    final V1Pod pod = entry.getKey();
                    try (@SuppressWarnings("unused") final MDC.MDCCloseable _podMDC = putNamespacedName("Pod", pod.getMetadata())) {
                        try {
                            final Status status = entry.getValue().get();

                            final InetAddress podIp = InetAddresses.forString(pod.getStatus().getPodIP());

                            final Status.OperationMode previousMode = cassandraNodeOperationModes.getIfPresent(podIp);
                            final Status.OperationMode mode = status.operationMode;

                            logger.debug("Cassandra node {} has OperationMode = {current: {}, previous: {}}.", podIp, mode, previousMode);

                            cassandraNodeOperationModes.put(podIp, mode);

                            if (previousMode != null && !previousMode.equals(mode)) {
                                eventBus.post(new CassandraNodeOperationModeChangedEvent(pod, dataCenterKey, previousMode, mode));
                            }

                        } catch (final Exception e) {
                            logger.warn("Failed to get Cassandra Pod status.", e);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(0, 1, TimeUnit.MINUTES);
    }
}
