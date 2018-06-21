package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.collect.*;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Resources;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.event.*;
import com.instaclustr.cassandra.operator.jmx.CassandraConnection;
import com.instaclustr.cassandra.operator.jmx.CassandraConnectionFactory;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.DataCenterSpec;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControllerService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(ControllerService.class);

    private static final DataCenterKey POISON = new DataCenterKey(null, null);

    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;
    private final Map<DataCenterKey, DataCenter> dataCenterCache;
    private final CassandraConnectionFactory cassandraConnectionFactory;
    private final K8sResourceUtils k8sResourceUtils;

    private final BlockingQueue<DataCenterKey> dataCenterQueue = new LinkedBlockingQueue<>();

    @Inject
    public ControllerService(final CoreV1Api coreApi,
                             final AppsV1beta2Api appsApi,
                             final Map<DataCenterKey, DataCenter> dataCenterCache,
                             final CassandraConnectionFactory cassandraConnectionFactory,
                             final K8sResourceUtils k8sResourceUtils) {
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        this.dataCenterCache = dataCenterCache;
        this.cassandraConnectionFactory = cassandraConnectionFactory;
        this.k8sResourceUtils = k8sResourceUtils;
    }

    @Subscribe
    void clusterEvent(final ClusterWatchEvent event) {
        logger.trace("Received ClusterWatchEvent {}.", event);
        // TODO: map the Cluster object to one or more DC objects, then post a message on the queue for them
    }

    @Subscribe
    void handleDataCenterEvent(final DataCenterWatchEvent event) {
        logger.trace("Received DataCenterWatchEvent {}.", event);
        dataCenterQueue.add(DataCenterKey.forDataCenter(event.dataCenter));
    }

    @Subscribe
    void handleSecretEvent(final SecretWatchEvent event) {
        logger.trace("Received SecretWatchEvent {}.", event);
        // TODO: handle updated/deleted secrets
    }

    @Subscribe
    void handleStatefulSetEvent(final StatefulSetWatchEvent event) {
        logger.trace("Received StatefulSetWatchEvent {}.", event);
        // TODO
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
                    deleteDataCenter(dataCenterKey);

                    return;
                }

                // data center created or modified
                try {
                    logger.info("Reconciling Data Center.");
                    createOrReplaceDataCenter(dataCenter);

                } catch (final Exception e) {
                    logger.warn("Failed to reconcile Data Center. This will be an exception in the future.", e);
                }
            }
        }
    }

    private void createOrReplaceDataCenter(final DataCenter dataCenter) throws Exception {
        // create the public service (what clients use to discover the data center)
        createOrReplaceNodesService(dataCenter);

        // create the seed-node service (what C* nodes use to discover the DC seeds)
        createOrReplaceSeedNodesService(dataCenter);

        // create configmap
        final V1ConfigMap configMap = createOrReplaceConfigMap(dataCenter);
        
        // create the statefulset for the DC nodes
        createOrReplaceStateNodesStatefulSet(dataCenter, configMap);
    }

    private static Map<String, String> dataCenterLabels(final DataCenter dataCenter) {
        return ImmutableMap.of("cassandra-datacenter", dataCenter.getMetadata().getName());
    }

    private void createOrReplaceStateNodesStatefulSet(final DataCenter dataCenter, final V1ConfigMap configMap) throws ApiException {
        final V1ObjectMeta dataCenterMetadata = dataCenter.getMetadata();
        final DataCenterSpec dataCenterSpec = dataCenter.getSpec();

        final Map<String, String> dataCenterLabels = dataCenterLabels(dataCenter);

        final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
                .metadata(new V1ObjectMeta()
                        .name(dataCenterMetadata.getName())
                        .namespace(dataCenterMetadata.getNamespace())
                        .labels(dataCenterLabels)
                )
                .spec(new V1beta2StatefulSetSpec()
                        .serviceName("cassandra")
                        .replicas(dataCenterSpec.getReplicas())
                        .selector(new V1LabelSelector().matchLabels(dataCenterLabels))
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta().labels(dataCenterLabels))
                                .spec(new V1PodSpec()
                                        .addContainersItem(new V1Container()
                                                .name(dataCenterMetadata.getName() + "-cassandra")
                                                .image(dataCenterSpec.getImage())
                                                .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                                                .ports(ImmutableList.of(
                                                        new V1ContainerPort().name("internode").containerPort(7000),
                                                        new V1ContainerPort().name("cql").containerPort(9042),
                                                        new V1ContainerPort().name("jmx").containerPort(7199)
                                                ))
                                                .resources(dataCenterSpec.getResources())
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
                                                .addVolumeMountsItem(new V1VolumeMount()
                                                .name("pod-info")
                                                .mountPath("/etc/podinfo"))
                                        )
                                        .addContainersItem(new V1Container()
                                                .name(dataCenterMetadata.getName() + "-sidecar")
                                                .env(dataCenter.getSpec().getEnv())
                                                .image("gcr.io/cassandra-operator/cassandra-sidecar-dev")
                                                .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                                                .ports(ImmutableList.of(
                                                        new V1ContainerPort().name("http").containerPort(4567)
                                                ))
//                                                .readinessProbe(new V1Probe()
//                                                        //.exec(new V1ExecAction().addCommandItem())
//                                                )
                                                .addVolumeMountsItem(new V1VolumeMount()
                                                        .name("data-volume")
                                                        .mountPath("/var/lib/cassandra")
                                                )
                                        )
                                        .addVolumesItem(new V1Volume()
                                                .name("config-volume")
                                                .configMap(new V1ConfigMapVolumeSource().name(configMap.getMetadata().getName()))
                                        )
                                        .addVolumesItem(new V1Volume()
                                            .name("pod-info")
                                            .downwardAPI(new V1DownwardAPIVolumeSource()
                                                    .addItemsItem(new V1DownwardAPIVolumeFile()
                                                            .path("labels")
                                                            .fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.labels"))
                                                    )
                                                    .addItemsItem(new V1DownwardAPIVolumeFile()
                                                            .path("annotations")
                                                            .fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.annotations"))
                                                    )
                                                    .addItemsItem(new V1DownwardAPIVolumeFile()
                                                            .path("namespace")
                                                            .fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.namespace"))
                                                    )
                                                    .addItemsItem(new V1DownwardAPIVolumeFile()
                                                            .path("name")
                                                            .fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.name"))
                                                    )
                                        ))
                                )
                        )
                        .addVolumeClaimTemplatesItem(new V1PersistentVolumeClaim()
                                .metadata(new V1ObjectMeta().name("data-volume"))
                                .spec(dataCenterSpec.getDataVolumeClaim())
                        )
                );

        // if the StatefulSet doesn't exist, create it. Otherwise scale it safely
        K8sResourceUtils.createOrReplaceResource(
                () -> appsApi.createNamespacedStatefulSet(statefulSet.getMetadata().getNamespace(), statefulSet, null),
                () -> replaceStatefulSet(dataCenter, statefulSet)
        );
    }

    private V1ConfigMap createOrReplaceConfigMap(final DataCenter dataCenter) throws IOException, ApiException {
        final V1ObjectMeta dataCenterMetadata = dataCenter.getMetadata();

        final V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .name(String.format("%s-config", dataCenterMetadata.getName()))
                        .namespace(dataCenterMetadata.getNamespace())
                        .labels(dataCenterLabels(dataCenter)))
                .putDataItem("cassandra.yaml", resourceAsString("/com/instaclustr/cassandra/config/cassandra.yaml") +
                        "\n\nseed_provider:\n" +
                        "    - class_name: com.instaclustr.cassandra.k8s.SeedProvider\n" +
                        "      parameters:\n" +
                        String.format("          - service: %s-seeds", dataCenterMetadata.getName())
                )
                .putDataItem("logback.xml", resourceAsString("/com/instaclustr/cassandra/config/logback.xml"))
                .putDataItem("cassandra-env.sh", resourceAsString("/com/instaclustr/cassandra/config/cassandra-env.sh"))
                .putDataItem("jvm.options", resourceAsString("/com/instaclustr/cassandra/config/jvm.options"));

        k8sResourceUtils.createOrReplaceNamespaceConfigMap(configMap);

        return configMap;
    }

    private void createOrReplaceSeedNodesService(final DataCenter dataCenter) throws ApiException {
        final V1ObjectMeta dataCenterMetadata = dataCenter.getMetadata();

        final Map<String, String> dataCenterLabels = dataCenterLabels(dataCenter);

        final V1Service service = new V1Service()
                .metadata(new V1ObjectMeta()
                        .name(String.format("%s-seeds", dataCenterMetadata.getName()))
                        .namespace(dataCenterMetadata.getNamespace())
                        .labels(dataCenterLabels)
                        .putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true")
                )
                .spec(new V1ServiceSpec()
                        .publishNotReadyAddresses(true)
                        .clusterIP("None")
                        // a port needs to be defined for the service to be resolvable (#there-was-a-bug-ID-and-now-I-cant-find-it)
                        .ports(ImmutableList.of(new V1ServicePort().name("internode").port(7000)))
                        .selector(dataCenterLabels)
                );

        k8sResourceUtils.createOrReplaceNamespaceService(service);
    }

    private void createOrReplaceNodesService(final DataCenter dataCenter) throws ApiException {
        final V1ObjectMeta dataCenterMetadata = dataCenter.getMetadata();

        final Map<String, String> dataCenterLabels = dataCenterLabels(dataCenter);

        final V1Service service = new V1Service()
                .metadata(new V1ObjectMeta()
                        .name(String.format("%s-nodes", dataCenterMetadata.getName()))
                        .namespace(dataCenterMetadata.getNamespace())
                        .labels(dataCenterLabels)
                )
                .spec(new V1ServiceSpec()
                        .clusterIP("None")
                        .ports(ImmutableList.of(
                                new V1ServicePort().name("cql").port(9042),
                                new V1ServicePort().name("jmx").port(7199)
                        ))
                        .selector(dataCenterLabels)
                );

        k8sResourceUtils.createOrReplaceNamespaceService(service);
    }

    private void deleteDataCenter(final DataCenterKey dataCenterKey) throws Exception {
        final String labelSelector = String.format("cassandra-datacenter=%s", dataCenterKey.name);

        // delete statefulset
        final V1beta2StatefulSetList statefulSets = appsApi.listNamespacedStatefulSet(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1beta2StatefulSet statefulSet : statefulSets.getItems()) {
            deleteStatefulSet(statefulSet);
        }

        // delete configmap
        final V1ConfigMapList configMaps = coreApi.listNamespacedConfigMap(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1ConfigMap configMap : configMaps.getItems()) {
            deleteConfigMap(configMap);
        }

        // delete services
        final V1ServiceList services = coreApi.listNamespacedService(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1Service service : services.getItems()) {
            deleteService(service);
        }
    }


    private static final Pattern STATEFUL_SET_POD_NAME_PATTERN = Pattern.compile(".*-(?<index>\\d+)");

    // comparator comparing StatefulSet Pods based on their names (which include an index)
    // "newest" pod first.
    private static final Comparator<V1Pod> STATEFUL_SET_POD_COMPARATOR = Comparator.comparingInt((V1Pod p) -> {
        final String podName = p.getMetadata().getName();
        final Matcher matcher = STATEFUL_SET_POD_NAME_PATTERN.matcher(podName);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("Pod %s name doesn't match expression %s", podName, STATEFUL_SET_POD_NAME_PATTERN));
        }

        return Integer.valueOf(matcher.group("index"));
    }).reversed();

    private void replaceStatefulSet(final DataCenter dataCenter, final V1beta2StatefulSet statefulSet) throws ApiException {
        final int currentReplicas;
        {
            final V1beta2StatefulSet existingStatefulSet = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), null, null, null);
            currentReplicas = existingStatefulSet.getSpec().getReplicas();
        }
        final int desiredReplicas = dataCenter.getSpec().getReplicas();

        // ideally this should use the same selector as the StatefulSet.
        // why does listNamespacedPod take a string for the selector when V1LabelSelector exists?
        final String labelSelector = String.format("cassandra-datacenter=%s", dataCenter.getMetadata().getName());

        // TODO: handle lists larger than the max returned (via continue attribute)
        final List<V1Pod> pods = ImmutableList.sortedCopyOf(STATEFUL_SET_POD_COMPARATOR,
                coreApi.listNamespacedPod(dataCenter.getMetadata().getNamespace(), null, null, null, null, labelSelector, null, null, null, null).getItems()
        );


        // TODO: this will be slow for a large number of nodes -- introduce some parallelism
        final ImmutableListMultimap<CassandraConnection.Status.OperationMode, V1Pod> podCassandraOperationModes = Multimaps.index(pods, p -> {
            final CassandraConnection cassandraConnection = cassandraConnectionFactory.connectionForPod(p);

            return cassandraConnection.status().operationMode;
        });


        if (EnumSet.of(CassandraConnection.Status.OperationMode.NORMAL, CassandraConnection.Status.OperationMode.DECOMMISSIONED).equals(podCassandraOperationModes.keySet())) {
            // change statefulset size

            // TODO: handle/assert that the newest pod is the one that was decommissioned

            statefulSet.getSpec().setReplicas(currentReplicas - 1);
            appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), statefulSet, null);

            return;
        }

        if (!EnumSet.of(CassandraConnection.Status.OperationMode.NORMAL).equals(podCassandraOperationModes.keySet())) {
            logger.warn("Skipping StatefulSet reconciliation as some Cassandra nodes are not in the NORMAL state: {}",
                    Multimaps.transformValues(podCassandraOperationModes, (V1Pod p) -> p.getMetadata().getName())
            );

            return;
        }


        if (desiredReplicas >= currentReplicas) {
            // scale up or modify
            appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), statefulSet, null);

        } else  {
            // scale down

            // decommission the newest pod
            cassandraConnectionFactory.connectionForPod(pods.get(0)).decommission();
        }
    }


    private static String resourceAsString(final String resourceName) throws IOException {
        return Resources.toString(Resources.getResource(ControllerService.class, resourceName), StandardCharsets.UTF_8);
    }


    private void deleteStatefulSet(final V1beta2StatefulSet statefulSet) throws Exception {
        V1DeleteOptions deleteOptions = new V1DeleteOptions();
        deleteOptions.setPropagationPolicy("Foreground");

        // TODO: verify if the below behaviour still exists in 1.6+
        // (as the ticket is closed)

//        deleteOptions.setPropagationPolicy("Foreground");
//
//        //Scale the statefulset down to zero (https://github.com/kubernetes/client-go/issues/91)
//        statefulSet.getSpec().setReplicas(0);
//
//        appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, statefulSet, null);
//
//        while (true) {
//            Integer currentReplicas = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, null, null, null).getStatus().getReplicas();
//            if (currentReplicas == 0)
//                break;
//
//            Thread.sleep(50);
//        }
//
//        logger.debug("done with scaling to 0");


        final V1ObjectMeta statefulSetMetadata = statefulSet.getMetadata();

        k8sResourceUtils.deleteResource(appsApi.deleteNamespacedStatefulSetCall(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), deleteOptions, null, null, null, "Foreground", null, null));
    }

    private void deleteConfigMap(final V1ConfigMap configMap) throws Exception {
        final V1DeleteOptions deleteOptions = new V1DeleteOptions();

        final V1ObjectMeta configMapMetadata = configMap.getMetadata();

        k8sResourceUtils.deleteResource(coreApi.deleteNamespacedConfigMapCall(configMapMetadata.getName(), configMapMetadata.getNamespace(), deleteOptions, null, null, null, null, null, null));
    }

    private void deleteService(final V1Service service) throws ApiException {
        final V1ObjectMeta serviceMetadata = service.getMetadata();

        k8sResourceUtils.deleteResource(coreApi.deleteNamespacedServiceCall(serviceMetadata.getName(), serviceMetadata.getNamespace(), null, null, null));
    }

    @Override
    protected void triggerShutdown() {
        dataCenterQueue.add(POISON);
    }
}
