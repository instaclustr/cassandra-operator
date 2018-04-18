package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractIdleService;
import com.instaclustr.cassandra.operator.event.ClusterEvent;
import com.instaclustr.cassandra.operator.event.DataCenterEvent;
import com.instaclustr.cassandra.operator.model.Cluster;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.*;
import io.kubernetes.client.proto.V1;
import io.kubernetes.client.proto.V1beta2Apps;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ControllerService extends AbstractExecutionThreadService {

    static final Logger logger = LoggerFactory.getLogger(ControllerService.class);


    static final DataCenterKey POISON = new DataCenterKey("", "");

    private final CustomObjectsApi customObjectsApi;
    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;

    private final BlockingQueue<DataCenterKey> dataCenterQueue = new LinkedBlockingQueue<>();

    @Inject
    public ControllerService(final CustomObjectsApi customObjectsApi,
                             final CoreV1Api coreApi,
                             final AppsV1beta2Api appsApi,
                             final Cache<DataCenterKey, DataCenter> dataCenterCache) {
        this.customObjectsApi = customObjectsApi;
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        this.dataCenterCache = dataCenterCache;
    }

    @Subscribe
    void clusterAdded(final ClusterEvent.Added event) {
        logger.info("Cluster added! {}", event.cluster);
    }

    @Subscribe
    void clusterModified(final ClusterEvent.Modified event) {
        logger.info("Cluster modified! {}", event.cluster);
    }

    @Subscribe
    void clusterDeleted(final ClusterEvent.Deleted event) {
        logger.info("Cluster deleted! {}", event.cluster);
    }


    @Subscribe
    void dataCenterEvent(final DataCenterEvent event) {
        dataCenterQueue.add(DataCenterKey.forDataCenter(event.dataCenter));
    }

    @Subscribe
    void dataCenterAdded(final DataCenterEvent.Added event) {
        logger.info("Data center added! {}", event.dataCenter);
    }

    @Subscribe
    void dataCenterModified(final DataCenterEvent.Modified event) {
        logger.info("Data center modified! {}", event.dataCenter);
    }

    @Subscribe
    void dataCenterDeleted(final DataCenterEvent.Deleted event) {
        logger.info("Data center deleted! {}", event.dataCenter);
    }


    @Override
    protected void startUp() throws Exception {}

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            final DataCenterKey dataCenterKey = dataCenterQueue.take();

            if (dataCenterKey == POISON)
                return;

            final DataCenter dataCenter = dataCenterCache.getIfPresent(dataCenterKey);

            if (dataCenter == null) {
                final V1DeleteOptions deleteOptions = new V1DeleteOptions();
                appsApi.deleteNamespacedStatefulSet("test-statefulset", "default", deleteOptions, null, null, null, null);

                continue;
            }

//            final V1Service service = new V1Service()
//                    .metadata(new V1ObjectMeta()
//                            .name("test-service")
//                            .labels(ImmutableMap.of("app", "cassandra"))
//                    )
//                    .spec(new V1ServiceSpec()
//                            .ports(ImmutableList.of(new V1ServicePort().name("cqlsh").port(9042)))
//                            .selector(ImmutableMap.of("app", "cassandra"))
//                    );
//
//            coreApi.createNamespacedService("default", service, null);


            final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
                    .metadata(new V1ObjectMeta()
                            .name("test-statefulset")
                            //.labels(ImmutableMap.of("app", "cassandra"))
                    )
                    .spec(new V1beta2StatefulSetSpec()
                            .serviceName("cassandra")
                            .replicas(dataCenter.getSpec().getReplicas().intValue())
                            .selector(new V1LabelSelector().putMatchLabelsItem("app", "cassandra"))
                            .template(new V1PodTemplateSpec()
                                    .metadata(new V1ObjectMeta().putLabelsItem("app", "cassandra"))
                                    .spec(new V1PodSpec()
                                            .addContainersItem(new V1Container()
                                                    .name("cassandra")
                                                    .image("busybox")
                                                    .command(ImmutableList.of("sh", "-c", "while true; do echo \"hello from ${HOSTNAME}\"; sleep 1; done"))
                                            )
                                    )
                            )
                    );

            try {
                appsApi.createNamespacedStatefulSet("default", statefulSet, null);


                logger.info("Created StatefulSet!");

            } catch (ApiException e) {
                if (e.getCode() == 409) { // HTTP 409 CONFLICT
                    appsApi.replaceNamespacedStatefulSet("test-statefulset", "default", statefulSet, null);

                    logger.info("StatefulSet already exists, replacing...");



                    continue;
                }

                throw e;
            }


        }
    }

    @Override
    protected void shutDown() throws Exception {
        dataCenterQueue.add(POISON); // TODO: this doesn't work properly in all cases
    }
}
