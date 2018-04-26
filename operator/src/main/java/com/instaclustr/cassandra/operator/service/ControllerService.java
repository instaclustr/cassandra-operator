package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractIdleService;
import com.instaclustr.cassandra.operator.event.ClusterEvent;
import com.instaclustr.cassandra.operator.event.DataCenterEvent;
import com.instaclustr.cassandra.operator.event.SecretEvent;
import com.instaclustr.cassandra.operator.event.StatefulSetEvent;
import com.instaclustr.cassandra.operator.model.Cluster;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;
import io.kubernetes.client.proto.V1;
import io.kubernetes.client.proto.V1beta2Apps;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ControllerService extends AbstractExecutionThreadService {

    static final Logger logger = LoggerFactory.getLogger(ControllerService.class);


    static final DataCenterKey POISON = new DataCenterKey("", "");

    private final String namespace = "default"; // TODO: get from injector (which is from config/cli/env)

    private final CustomObjectsApi customObjectsApi;
    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;

    private final BlockingQueue<DataCenterKey> dataCenterQueue = new LinkedBlockingQueue<>();

    @Inject
    ControllerService(final CustomObjectsApi customObjectsApi,
                             final CoreV1Api coreApi,
                             final AppsV1beta2Api appsApi,
                             final Cache<DataCenterKey, DataCenter> dataCenterCache) {
        this.customObjectsApi = customObjectsApi;
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        this.dataCenterCache = dataCenterCache;
    }

    @Subscribe
    void clusterEvent(final ClusterEvent event) {
        // TODO: map the Cluster object to one or more DC objects, then post a message on the queue for them
    }

    @Subscribe
    void dataCenterEvent(final DataCenterEvent event) {
        dataCenterQueue.add(DataCenterKey.forDataCenter(event.dataCenter));
    }

    @Subscribe
    void secretEvent(final SecretEvent event) {
        // TODO: handle updated/deleted secrets
    }

    @Subscribe
    void statefulSetEvent(final StatefulSetEvent event) {
        // TODO
    }


    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            final DataCenterKey dataCenterKey = dataCenterQueue.take();

            if (dataCenterKey == POISON)
                return;

            final DataCenter dataCenter = dataCenterCache.getIfPresent(dataCenterKey);

            if (dataCenter == null) {
                appsApi.deleteNamespacedStatefulSet(dataCenterKey.name, namespace, null, null, null, null, null);

                continue;
            }

            logger.debug("Coalescing state for Cassandra Data Center {}", dataCenterKey);

            // create the public service (what clients use to discover the data center)
            {
                final V1Service service = new V1Service()
                        .metadata(new V1ObjectMeta()
                                        .name(dataCenterKey.name)
//                            .labels(ImmutableMap.of("app", "cassandra"))
                        )
                        .spec(new V1ServiceSpec()
                                .clusterIP("None")
                                .ports(ImmutableList.of(new V1ServicePort().name("cql").port(9042)))
                                .selector(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
                        );

                createOrReplaceNamespaceService(service);
            }

            // create the seed-node service (what C* nodes use to discover the DC seeds)
            {
                final V1Service service = new V1Service()
                        .metadata(new V1ObjectMeta()
                                .name(String.format("%s-seeds", dataCenterKey.name))
                                .putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true")
                        )
                        .spec(new V1ServiceSpec()
                                .publishNotReadyAddresses(true)
                                .clusterIP("None")
                                // a port needs to be defined for the service to be resolvable (#there-was-a-bug-ID-and-now-I-cant-find-it)
                                .ports(ImmutableList.of(new V1ServicePort().name("internode").port(7000)))
                                .selector(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
                        );

                createOrReplaceNamespaceService(service);
            }

            // create config map
            final V1ConfigMap configMap;
            {
                configMap = new V1ConfigMap()
                        .metadata(new V1ObjectMeta().name(String.format("%s-config", dataCenterKey.name)))
                        .putDataItem("cassandra.yaml", resourceAsString("/com/instaclustr/cassandra/config/cassandra.yaml") +
                            "\n\nseed_provider:\n" +
                                    "    - class_name: com.instaclustr.cassandra.k8s.SeedProvider\n" +
                                    "      parameters:\n" +
                                    String.format("          - service: %s-seeds", dataCenterKey.name)
                        )
                        .putDataItem("logback.xml", resourceAsString("/com/instaclustr/cassandra/config/logback.xml"))
                        .putDataItem("cassandra-env.sh", resourceAsString("/com/instaclustr/cassandra/config/cassandra-env.sh"))
                        .putDataItem("jvm.options", resourceAsString("/com/instaclustr/cassandra/config/jvm.options"))
                ;

                createOrReplaceNamespaceConfigMap(configMap);
            }

            // create the stateful set for the DC nodes
            {
                final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
                        .metadata(new V1ObjectMeta()
                                        .name(dataCenterKey.name)
                                //.labels(ImmutableMap.of("app", "cassandra"))
                        )
                        .spec(new V1beta2StatefulSetSpec()
                                .serviceName("cassandra")
                                .replicas(dataCenter.getSpec().getReplicas().intValue())
                                .selector(new V1LabelSelector().putMatchLabelsItem("cassandra-datacenter", dataCenterKey.name))
                                .template(new V1PodTemplateSpec()
                                        .metadata(new V1ObjectMeta().putLabelsItem("cassandra-datacenter", dataCenterKey.name))
                                        .spec(new V1PodSpec()
                                                .addContainersItem(new V1Container()
                                                        .name(dataCenterKey.name)
                                                        .image("instaclustr/k8s-cassandra") // TODO: parameterize version (not sure if we should expose the whole image as configurable)
                                                        .imagePullPolicy("Never")
                                                        .ports(ImmutableList.of(
                                                                new V1ContainerPort().name("internode").containerPort(7000),
                                                                new V1ContainerPort().name("cql").containerPort(9042),
                                                                new V1ContainerPort().name("jmx").containerPort(7199)
                                                        ))
                                                        .readinessProbe(new V1Probe()
                                                                .exec(new V1ExecAction().addCommandItem("/usr/bin/readiness-probe"))
                                                                .initialDelaySeconds(60)
                                                                .timeoutSeconds(5)
                                                        )
                                                        .addVolumeMountsItem(new V1VolumeMount()
                                                                .name("config-volume")
                                                                .mountPath("/etc/cassandra")
                                                        )
                                                        .addVolumeMountsItem(new V1VolumeMount()
                                                                .name("data-volume")
                                                                .mountPath("/var/lib/cassandra")
                                                        )
                                                )
                                                .addVolumesItem(new V1Volume()
                                                        .name("config-volume")
                                                        .configMap(new V1ConfigMapVolumeSource().name(configMap.getMetadata().getName()))
                                                )
                                        )
                                )
                                .addVolumeClaimTemplatesItem(new V1PersistentVolumeClaim()
                                        .metadata(new V1ObjectMeta().name("data-volume"))
                                        .spec(new V1PersistentVolumeClaimSpec()
                                                .addAccessModesItem("ReadWriteOnce")
                                                .resources(new V1ResourceRequirements().putRequestsItem("storage", Quantity.fromString("100Mi"))) // TODO: parameterize
                                        )
                                )
                        );

                createOrReplaceNamespaceStatefulSet(statefulSet);
            }
        }
    }



    @FunctionalInterface
    private interface ApiCallable {
        void call() throws ApiException;
    }

    private static void createOrReplaceResource(final ApiCallable createCallable, final ApiCallable replaceCallable) throws ApiException {
        try {
            createCallable.call();

        } catch (final ApiException e) {
            if (e.getCode() != 409)
                throw e;

            replaceCallable.call();
        }
    }

    private void createOrReplaceNamespaceStatefulSet(final V1beta2StatefulSet statefulSet) throws ApiException {
        createOrReplaceResource(
                () -> appsApi.createNamespacedStatefulSet(namespace, statefulSet, null),
                () -> appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, statefulSet, null)
        );
    }


    private void createOrReplaceNamespaceService(final V1Service service) throws ApiException {
        createOrReplaceResource(
                () -> coreApi.createNamespacedService(namespace, service, null),
                () -> {return;} //coreApi.replaceNamespacedService(service.getMetadata().getName(), namespace, service, null)

        );
    }

    private void createOrReplaceNamespaceConfigMap(final V1ConfigMap configMap) throws ApiException {
        createOrReplaceResource(
                () -> coreApi.createNamespacedConfigMap(namespace, configMap, null),
                () -> coreApi.replaceNamespacedConfigMap(configMap.getMetadata().getName(), namespace, configMap, null)
        );
    }

    public static String resourceAsString(final String resourceName) throws IOException {
        return Resources.toString(Resources.getResource(ControllerService.class, resourceName), StandardCharsets.UTF_8);
    }

    @Override
    protected void triggerShutdown() {
        dataCenterQueue.add(POISON);
    }
}
