package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.instaclustr.cassandra.operator.event.CassandraNodeStatusChangedEvent;
import com.instaclustr.cassandra.operator.jmx.CassandraConnection;
import com.instaclustr.cassandra.operator.jmx.CassandraConnectionFactory;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CassandraHealthCheckService extends AbstractScheduledService {
    private static final Logger logger = LoggerFactory.getLogger(CassandraHealthCheckService.class);

    private final CoreV1Api coreApi;
    private final Map<DataCenterKey, DataCenter> dataCenterCache;
    private final CassandraConnectionFactory cassandraConnectionFactory;
    private final EventBus eventBus;

    private Cache<InetAddress, CassandraConnection.Status> cassandraNodeStatuses = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();


    @Inject
    public CassandraHealthCheckService(final CoreV1Api coreApi,
                                       final Map<DataCenterKey, DataCenter> dataCenterCache,
                                       final CassandraConnectionFactory cassandraConnectionFactory,
                                       final EventBus eventBus) {
        this.coreApi = coreApi;
        this.dataCenterCache = dataCenterCache;
        this.cassandraConnectionFactory = cassandraConnectionFactory;
        this.eventBus = eventBus;
    }


    @Override
    protected void runOneIteration() throws Exception {
        // TODO: maybe this would be better off querying k8s for the service endpoints rather than the pods themselves...

        logger.debug("Checking health of cassandra instances...");

        for (final Map.Entry<DataCenterKey, DataCenter> cacheEntry : dataCenterCache.entrySet()) {
            final DataCenterKey dataCenterKey = cacheEntry.getKey();

            final String labelSelector = String.format("cassandra-datacenter=%s", dataCenterKey.name);

            final V1PodList podList = coreApi.listNamespacedPod(cacheEntry.getValue().getMetadata().getNamespace(), null, null, null, null, labelSelector, null, null, null, null);

            for (final V1Pod pod : podList.getItems()) {
                final InetAddress podIp = InetAddresses.forString(pod.getStatus().getPodIP());

                try {
                    logger.debug("{} pod {} has IP {}", dataCenterKey, pod.getMetadata().getName(), podIp);

                    final CassandraConnection cassandraConnection = cassandraConnectionFactory.connectionForAddress(podIp);

                    final CassandraConnection.Status previousStatus = cassandraNodeStatuses.getIfPresent(podIp);
                    final CassandraConnection.Status status = cassandraConnection.status();

                    logger.debug("{}", status);

                    if (previousStatus != null && !previousStatus.equals(status)) {
                        eventBus.post(new CassandraNodeStatusChangedEvent(pod, dataCenterKey, previousStatus, status));
                    }

                    cassandraNodeStatuses.put(podIp, status);

                } catch (Exception e) {
                    logger.warn("Failed to check the status of Cassandra node {} ({}). This will be an excpetion in the future.", pod.getMetadata().getName(), podIp, e);
                }
            }
        }
    }



    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(0, 1, TimeUnit.MINUTES);
    }
}
