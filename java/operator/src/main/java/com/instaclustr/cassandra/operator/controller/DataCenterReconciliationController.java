package com.instaclustr.cassandra.operator.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.jmx.CassandraConnection;
import com.instaclustr.cassandra.operator.jmx.CassandraConnectionFactory;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.DataCenterSpec;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataCenterReconciliationController {
    private static final Logger logger = LoggerFactory.getLogger(DataCenterReconciliationController.class);

    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;
    private final CustomObjectsApi customObjectsApi;
    private final CassandraConnectionFactory cassandraConnectionFactory;
    private final K8sResourceUtils k8sResourceUtils;

    private final V1ObjectMeta dataCenterMetadata;
    private final DataCenterSpec dataCenterSpec;

    private final Map<String, String> dataCenterLabels;

    @Inject
    public DataCenterReconciliationController(final CoreV1Api coreApi,
                                              final AppsV1beta2Api appsApi,
                                              final CustomObjectsApi customObjectsApi,
                                              final CassandraConnectionFactory cassandraConnectionFactory,
                                              final K8sResourceUtils k8sResourceUtils,
                                              @Assisted final DataCenter dataCenter) {
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        this.customObjectsApi = customObjectsApi;
        this.cassandraConnectionFactory = cassandraConnectionFactory;
        this.k8sResourceUtils = k8sResourceUtils;

        this.dataCenterMetadata = dataCenter.getMetadata();
        this.dataCenterSpec = dataCenter.getSpec();

        this.dataCenterLabels = ImmutableMap.of("cassandra-datacenter", dataCenterMetadata.getName());
    }

    public void reconcileDataCenter() throws Exception {
        // create the public service (what clients use to discover the data center)
        createOrReplaceNodesService();

        // create the seed-node service (what C* nodes use to discover the DC seeds)
        createOrReplaceSeedNodesService();

        // create configmaps and their volume mounts (operator-defined config, and user overrides)
        final List<ConfigMapVolumeMount> configMapVolumeMounts;
        {
            final ImmutableList.Builder<ConfigMapVolumeMount> builder = ImmutableList.builder();

            builder.add(createOrReplaceOperatorConfigMap());

            if (dataCenterSpec.getUserConfigMapVolumeSource() != null) {
                builder.add(new ConfigMapVolumeMount("user-config-volume", "/tmp/user-config", dataCenterSpec.getUserConfigMapVolumeSource()));
            }

            configMapVolumeMounts = builder.build();
        }

        // create the statefulset for the DC nodes
        createOrReplaceStateNodesStatefulSet(configMapVolumeMounts);
    }



    private String dataCenterChildObjectName(final String nameFormat) {
        return String.format(nameFormat, dataCenterMetadata.getName());
    }

    private V1ObjectMeta dataCenterChildObjectMetadata(final String nameFormat) {
        return new V1ObjectMeta()
                .name(dataCenterChildObjectName(nameFormat))
                .namespace(dataCenterMetadata.getNamespace())
                .labels(dataCenterLabels);
    }

    private static class ConfigMapVolumeMount {
        final String name, mountPath;

        final V1ConfigMapVolumeSource volumeSource;

        private ConfigMapVolumeMount(final String name, final String mountPath, final V1ConfigMapVolumeSource volumeSource) {
            this.name = name;
            this.mountPath = mountPath;
            this.volumeSource = volumeSource;
        }
    }

    private void createOrReplaceStateNodesStatefulSet(final Iterable<ConfigMapVolumeMount> configMapVolumeMounts) throws ApiException {
        final V1Container cassandraContainer = new V1Container()
                .name(dataCenterChildObjectName("%s-cassandra"))
                .image(dataCenterSpec.getCassandraImage())
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
                        .name("data-volume")
                        .mountPath("/var/lib/cassandra")
                )
                .addVolumeMountsItem(new V1VolumeMount()
                        .name("pod-info")
                        .mountPath("/etc/podinfo")
                );


        final V1Container sidecarContainer = new V1Container()
                .name(dataCenterChildObjectName("%s-sidecar"))
                .env(dataCenterSpec.getEnv())
                .image(dataCenterSpec.getSidecarImage())
                .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                .ports(ImmutableList.of(
                        new V1ContainerPort().name("http").containerPort(4567)))
                .addVolumeMountsItem(new V1VolumeMount()
                        .name("data-volume")
                        .mountPath("/var/lib/cassandra")
                );


        final V1PodSpec podSpec = new V1PodSpec()
                .addContainersItem(cassandraContainer)
                .addContainersItem(sidecarContainer)
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
                        )
                );


        // add configmap volumes
        for (final ConfigMapVolumeMount configMapVolumeMount : configMapVolumeMounts) {
            cassandraContainer.addVolumeMountsItem(new V1VolumeMount()
                    .name(configMapVolumeMount.name)
                    .mountPath(configMapVolumeMount.mountPath)
            );

            // the Cassandra container entrypoint overlays configmap volumes
            cassandraContainer.addArgsItem(configMapVolumeMount.mountPath);

            podSpec.addVolumesItem(new V1Volume()
                    .name(configMapVolumeMount.name)
                    .configMap(configMapVolumeMount.volumeSource)
            );
        }


        if (dataCenterSpec.getRestoreFromBackup() != null) {

//            Custom objects api object doesn't give us a nice way to pass in the type we want so we do it manually
            Call call = customObjectsApi.getNamespacedCustomObjectCall("stable.instaclustr.com", "v1", "default", "cassandra-backups", dataCenterSpec.getRestoreFromBackup(), null, null);

            Backup backup = (Backup) customObjectsApi.getApiClient().execute(call, new TypeToken<Backup>(){}.getType()).getData();

            podSpec.addInitContainersItem(new V1Container()
                    .name(dataCenterChildObjectName("*-sidecar-restore"))
                    .env(dataCenterSpec.getEnv())
                    .image(dataCenterSpec.getSidecarImage())
                    .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                    .command(ImmutableList.of(
                            "java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap",
                            "-cp", "/opt/lib/cassandra-sidecar/cassandra-sidecar.jar",
                            "com.instaclustr.cassandra.sidecar.SidecarRestore",
                            "-bb", backup.getSpec().getTarget(),
                            "-c", backup.getMetadata().getLabels().get("cassandra-datacenter"),
                            "-bi", backup.getMetadata().getLabels().get("cassandra-datacenter"),
                            "-s", backup.getMetadata().getName(),
                            "--bs", backup.getSpec().getBackupType(),
                            "-rs"
                    ))
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("pod-info")
                            .mountPath("/etc/podinfo"))
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("config-volume")
                            .mountPath("/etc/cassandra")
                    ).addVolumeMountsItem(new V1VolumeMount()
                            .name("data-volume")
                            .mountPath("/var/lib/cassandra")
                    )
            );
        }

        final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
                .metadata(dataCenterChildObjectMetadata("%s-statefulset"))
                .spec(new V1beta2StatefulSetSpec()
                        .serviceName("cassandra")
                        .replicas(dataCenterSpec.getReplicas())
                        .selector(new V1LabelSelector().matchLabels(dataCenterLabels))
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta().labels(dataCenterLabels))
                                .spec(podSpec)
                        )
                        .addVolumeClaimTemplatesItem(new V1PersistentVolumeClaim()
                                .metadata(new V1ObjectMeta().name("data-volume"))
                                .spec(dataCenterSpec.getDataVolumeClaim())
                        )
                );

        // if the StatefulSet doesn't exist, create it. Otherwise scale it safely
        K8sResourceUtils.createOrReplaceResource(
                () -> appsApi.createNamespacedStatefulSet(statefulSet.getMetadata().getNamespace(), statefulSet, null),
                () -> replaceStatefulSet(statefulSet)
        );
    }

//    private V1ConfigMap createOrReplaceCassandraConfigMap(final DataCenter dataCenter) throws IOException, ApiException {
//        final V1ObjectMeta dataCenterMetadata = dataCenter.getMetadata();
//
//        final V1ConfigMap configMap = new V1ConfigMap()
//                .metadata(new V1ObjectMeta()
//                        .name(String.format("%s-cassandra-config", dataCenterMetadata.getName()))
//                        .namespace(dataCenterMetadata.getNamespace())
//                        .labels(dataCenterLabels(dataCenter)))
//                .putDataItem("cassandra.yaml", resourceAsString("/com/instaclustr/cassandra/config/cassandra.yaml") +
//                        "\n\nseed_provider:\n" +
//                        "    - class_name: com.instaclustr.cassandra.k8s.SeedProvider\n" +
//                        "      parameters:\n" +
//                        String.format("          - service: %s-seeds", dataCenterMetadata.getName())
//                );
//
//        k8sResourceUtils.createOrReplaceNamespaceConfigMap(configMap);
//        return configMap;
//    }



//    private static void configMapVolumeAddFile(final V1ConfigMap configMap, final V1ConfigMapVolumeSource volumeSource, final Path path, final String content) {
//
//
//        path.toString().
//    }



    private ConfigMapVolumeMount createOrReplaceOperatorConfigMap() throws IOException, ApiException {
        final V1ConfigMap configMap = new V1ConfigMap()
                .metadata(dataCenterChildObjectMetadata("%s-operator-config"));

        final V1ConfigMapVolumeSource configMapVolumeSource = new V1ConfigMapVolumeSource().name(configMap.getMetadata().getName());

        configMap.putDataItem("001-seed-provider.yaml", "#hello world");
        configMapVolumeSource.addItemsItem(new V1KeyToPath().key("001-seed-provider.yaml").path("cassandra.yaml.d/001-seed-provider.yaml"));

        k8sResourceUtils.createOrReplaceNamespaceConfigMap(configMap);

        return new ConfigMapVolumeMount("operator-config-volume", "/tmp/operator-config", configMapVolumeSource);
    }

    private void createOrReplaceSeedNodesService() throws ApiException {
        final V1Service service = new V1Service()
                .metadata(dataCenterChildObjectMetadata("%s-seeds")
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

    private void createOrReplaceNodesService() throws ApiException {
        final V1Service service = new V1Service()
                .metadata(dataCenterChildObjectMetadata("%s-nodes"))
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

    private void replaceStatefulSet(final V1beta2StatefulSet statefulSet) throws ApiException {
        final V1ObjectMeta statefulSetMetadata = statefulSet.getMetadata();

        final int currentReplicas;
        {
            final V1beta2StatefulSet existingStatefulSet = appsApi.readNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), null, null, null);
            currentReplicas = existingStatefulSet.getSpec().getReplicas();
        }
        final int desiredReplicas = dataCenterSpec.getReplicas();

        // ideally this should use the same selector as the StatefulSet.
        // why does listNamespacedPod take a string for the selector when V1LabelSelector exists?
        final String labelSelector = String.format("cassandra-datacenter=%s", dataCenterMetadata.getName());

        // TODO: handle lists larger than the max returned (via continue attribute)
        final List<V1Pod> pods = ImmutableList.sortedCopyOf(STATEFUL_SET_POD_COMPARATOR,
                coreApi.listNamespacedPod(dataCenterMetadata.getNamespace(), null, null, null, null, labelSelector, null, null, null, null).getItems()
        );


        // TODO: this will be slow for a large number of nodes -- introduce some parallelism
        final ImmutableListMultimap<CassandraConnection.Status.OperationMode, V1Pod> podCassandraOperationModes = Multimaps.index(
                pods.stream().filter(p -> p.getStatus().getPhase().equals("Running")).collect(Collectors.toList()),
                p -> {
                    final CassandraConnection cassandraConnection = cassandraConnectionFactory.connectionForPod(p);

                    return cassandraConnection.status().operationMode;
                });


        if (EnumSet.of(CassandraConnection.Status.OperationMode.NORMAL, CassandraConnection.Status.OperationMode.DECOMMISSIONED).equals(podCassandraOperationModes.keySet())) {
            // change statefulset size

            // TODO: handle/assert that the newest pod is the one that was decommissioned

            statefulSet.getSpec().setReplicas(currentReplicas - 1); // todo: modify a copy
            appsApi.replaceNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), statefulSet, null);

            try {
                k8sResourceUtils.deletePersistentVolumeAndPersistentVolumeClaim(pods.get(0));
            } catch (final Exception e) {
                logger.warn("Failed to delete PersistentVolume & PersistentVolumeClaim. Reason is: ", e);
            }

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
            appsApi.replaceNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), statefulSet, null);

        } else  {
            // scale down

            // decommission the newest pod
            cassandraConnectionFactory.connectionForPod(pods.get(0)).decommission();
        }
    }
}
