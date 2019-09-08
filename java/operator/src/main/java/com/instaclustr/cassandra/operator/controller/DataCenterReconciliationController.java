package com.instaclustr.cassandra.operator.controller;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.configuration.DeletePVC;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.DataCenterSpec;
import com.instaclustr.cassandra.operator.sidecar.SidecarClient;
import com.instaclustr.cassandra.operator.sidecar.SidecarClientFactory;
import com.instaclustr.cassandra.sidecar.model.Status;
import com.instaclustr.k8s.K8sLabels;
import com.instaclustr.k8s.LabelSelectors;
import com.instaclustr.slf4j.MDC;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport.putNamespacedName;

public class DataCenterReconciliationController {
    private static final Logger logger = LoggerFactory.getLogger(DataCenterReconciliationController.class);

    private final AppsV1beta2Api appsApi;
    private final CustomObjectsApi customObjectsApi;
    private final SidecarClientFactory sidecarClientFactory;

    private final K8sResourceUtils k8sResourceUtils;

    private final V1ObjectMeta dataCenterMetadata;
    private final DataCenterSpec dataCenterSpec;

    private final Map<String, String> dataCenterLabels;
    private final boolean allowCleanups;

    @Inject
    public DataCenterReconciliationController(final AppsV1beta2Api appsApi,
                                              final CustomObjectsApi customObjectsApi,
                                              final SidecarClientFactory sidecarClientFactory,
                                              final K8sResourceUtils k8sResourceUtils,
                                              @DeletePVC final boolean allowCleanups,
                                              @Assisted final DataCenter dataCenter) {
        this.appsApi = appsApi;
        this.customObjectsApi = customObjectsApi;
        this.sidecarClientFactory = sidecarClientFactory;
        this.k8sResourceUtils = k8sResourceUtils;

        this.dataCenterMetadata = dataCenter.getMetadata();
        this.dataCenterSpec = dataCenter.getSpec();
        this.allowCleanups = allowCleanups;

        this.dataCenterLabels = ImmutableMap.of(
                OperatorLabels.DATACENTER, dataCenterMetadata.getName(),
                K8sLabels.MANAGED_BY, OperatorLabels.OPERATOR_IDENTIFIER
                // TODO: add other recommended labels
        );
    }

    public void reconcileDataCenter() throws Exception {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterMDC = putNamespacedName("DataCenter", dataCenterMetadata)) {
            logger.info("Reconciling DataCenter.");

            // create the public service (what clients use to discover the data center)
            createOrReplaceNodesService();

            // create the seed-node service (what C* nodes use to discover the DC seeds)
            final V1Service seedNodesService = createOrReplaceSeedNodesService();

            // create configmaps and their volume mounts (operator-defined config, and user overrides)
            final List<ConfigMapVolumeMount> configMapVolumeMounts;
            {
                final ImmutableList.Builder<ConfigMapVolumeMount> builder = ImmutableList.builder();

                builder.add(createOrReplaceOperatorConfigMap(seedNodesService));

                if (dataCenterSpec.getUserConfigMapVolumeSource() != null) {
                    builder.add(new ConfigMapVolumeMount("user-config-volume", "/tmp/user-config", dataCenterSpec.getUserConfigMapVolumeSource()));
                }

                configMapVolumeMounts = builder.build();
            }

            // create the StatefulSet for the DC nodes
            createOrReplaceStateNodesStatefulSet(configMapVolumeMounts, dataCenterSpec.getUserSecretVolumeSource());

            if (dataCenterSpec.getPrometheusSupport()) {
                createOrReplacePrometheusServiceMonitor();
            }

            logger.info("Reconciled DataCenter.");
        }
    }

    private String dataCenterChildObjectName(final String nameFormat) {
        return String.format("cassandra-" + nameFormat, dataCenterMetadata.getName());
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

    private void createOrReplaceStateNodesStatefulSet(final Iterable<ConfigMapVolumeMount> configMapVolumeMounts, final V1SecretVolumeSource secretVolumeSource) throws ApiException {
        final V1ObjectMeta statefulSetMetadata = dataCenterChildObjectMetadata("%s");

        try (@SuppressWarnings("unused") final MDC.MDCCloseable _statefulSetMDC = putNamespacedName("StatefulSet", statefulSetMetadata)) {


            final V1Container cassandraContainer = new V1Container()
                    .name("cassandra")
                    .image(dataCenterSpec.getCassandraImage())
                    .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                    .addPortsItem(new V1ContainerPort().name("internode").containerPort(7000))
                    .addPortsItem(new V1ContainerPort().name("internode-ssl").containerPort(7001))
                    .addPortsItem(new V1ContainerPort().name("cql").containerPort(9042))
                    .addPortsItem(new V1ContainerPort().name("jmx").containerPort(7199))
                    .resources(dataCenterSpec.getResources())
                    .securityContext(new V1SecurityContext().capabilities(new V1Capabilities().add(ImmutableList.of(
                            "IPC_LOCK",
                            "SYS_RESOURCE"
                    ))))
                    .readinessProbe(new V1Probe()
                            .exec(new V1ExecAction().addCommandItem("/usr/bin/cql-readiness-probe"))
                            .initialDelaySeconds(60)
                            .timeoutSeconds(5)
                    )
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("cassandra-data-volume")
                            .mountPath("/var/lib/cassandra")
                    )
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("pod-info")
                            .mountPath("/etc/podinfo")
                    )
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("sidecar-config-volume")
                            .mountPath("/tmp/sidecar-config-volume")
                    )
                    .addArgsItem("/tmp/sidecar-config-volume")
                    .addEnvItem(new V1EnvVar().name("NAMESPACE").valueFrom(new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.namespace"))))
                    .addEnvItem(new V1EnvVar().name("POD_NAME").valueFrom(new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath("metadata.name"))))
                    .addEnvItem(new V1EnvVar().name("POD_IP").valueFrom(new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath("status.podIP"))));

            if (dataCenterSpec.getPrometheusSupport()) {
                cassandraContainer.addPortsItem(new V1ContainerPort().name("prometheus").containerPort(9500));
            }

            final V1Container sidecarContainer = new V1Container()
                    .name("sidecar")
                    .env(dataCenterSpec.getEnv())
                    .image(dataCenterSpec.getSidecarImage())
                    .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                    .addPortsItem(new V1ContainerPort().name("http").containerPort(4567))
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("cassandra-data-volume")
                            .mountPath("/var/lib/cassandra")
                    )
                    .addVolumeMountsItem(new V1VolumeMount()
                            .name("sidecar-config-volume")
                            .mountPath("/tmp/sidecar-config-volume")
                    );


            final V1PodSpec podSpec = new V1PodSpec()
                    .addInitContainersItem(fileLimitInit())
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
                    )
                    .addVolumesItem(new V1Volume()
                            .name("sidecar-config-volume")
                            .emptyDir(new V1EmptyDirVolumeSource())
                    )
                    .affinity(dataCenterSpec.getAffinity());
            List<V1Toleration> tolerations = dataCenterSpec.getTolerations();
            if(tolerations != null) {
                tolerations.forEach((V1Toleration toleration) -> podSpec.addTolerationsItem(toleration));
            }

            {
                final String secret = dataCenterSpec.getImagePullSecret();
                if (!Strings.isNullOrEmpty(secret)) {
                    final V1LocalObjectReference pullSecret = new V1LocalObjectReference()
                            .name(secret);

                    podSpec.addImagePullSecretsItem(pullSecret);
                }
            }


            // add configmap volumes
            for (final ConfigMapVolumeMount configMapVolumeMount : configMapVolumeMounts) {
                cassandraContainer.addVolumeMountsItem(new V1VolumeMount()
                        .name(configMapVolumeMount.name)
                        .mountPath(configMapVolumeMount.mountPath)
                );

                // provide access to config map volumes in the sidecar, these reside in /tmp though and are not overlayed into /etc/cassandra
                sidecarContainer.addVolumeMountsItem(new V1VolumeMount()
                        .name(configMapVolumeMount.name)
                        .mountPath(configMapVolumeMount.mountPath));

                // the Cassandra container entrypoint overlays configmap volumes
                cassandraContainer.addArgsItem(configMapVolumeMount.mountPath);

                podSpec.addVolumesItem(new V1Volume()
                        .name(configMapVolumeMount.name)
                        .configMap(configMapVolumeMount.volumeSource)
                );
            }

            if (secretVolumeSource != null) {
                cassandraContainer.addVolumeMountsItem(new V1VolumeMount()
                        .name("user-secret-volume")
                        .mountPath("/tmp/user-secret-config"));

                podSpec.addVolumesItem(new V1Volume()
                        .name("user-secret-volume")
                        .secret(secretVolumeSource)
                );
            }


            if (dataCenterSpec.getRestoreFromBackup() != null) {
                logger.debug("Restore requested.");

                // custom objects api doesn't give us a nice way to pass in the type we want so we do it manually
                final Backup backup;
                {
                    final Call call = customObjectsApi.getNamespacedCustomObjectCall("stable.instaclustr.com", "v1", "default", "cassandra-backups", dataCenterSpec.getRestoreFromBackup(), null, null);
                    backup = customObjectsApi.getApiClient().<Backup>execute(call, new TypeToken<Backup>() {}.getType()).getData();
                }

                podSpec.addInitContainersItem(new V1Container()
                                .name("sidecar-restore")
                                .env(dataCenterSpec.getEnv())
                                .image(dataCenterSpec.getSidecarImage())
                                .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                                .command(ImmutableList.of(
                                        "java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap",
                                        "-cp", "/opt/lib/cassandra-sidecar/cassandra-sidecar.jar",
                                        "com.instaclustr.cassandra.sidecar.SidecarRestore",
                                        "-bb", backup.getSpec().getTarget(), // bucket name
                                        "-c", dataCenterMetadata.getName(), // clusterID == DcName. Backup dc and restore dc must have the same name
                                        "-bi", dataCenterChildObjectName("%s"), // pod name prefix
                                        "-s", backup.getMetadata().getName(), // backup tag used to find the manifest file
                                        "--bs", backup.getSpec().getBackupType(),
                                        "-rs",
                                        "--cd", "/tmp/sidecar-config-volume" // location where the restore task can write config fragments
                                ))
                                .addVolumeMountsItem(new V1VolumeMount()
                                        .name("pod-info")
                                        .mountPath("/etc/podinfo")
                                ).addVolumeMountsItem(new V1VolumeMount()
                                        .name("sidecar-config-volume")
                                        .mountPath("/tmp/sidecar-config-volume")
                                ).addVolumeMountsItem(new V1VolumeMount()
                                        .name("cassandra-data-volume")
                                        .mountPath("/var/lib/cassandra")
                                )
                        );
            }

            final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
                    .metadata(statefulSetMetadata)
                    .spec(new V1beta2StatefulSetSpec()
                            .serviceName("cassandra")
                            .replicas(dataCenterSpec.getReplicas())
                            .selector(new V1LabelSelector().matchLabels(dataCenterLabels))
                            .template(new V1PodTemplateSpec()
                                    .metadata(new V1ObjectMeta().labels(dataCenterLabels))
                                    .spec(podSpec)
                            )
                            .addVolumeClaimTemplatesItem(new V1PersistentVolumeClaim()
                                    .metadata(new V1ObjectMeta().name("cassandra-data-volume"))
                                    .spec(dataCenterSpec.getDataVolumeClaim())
                            )
                    );

            // if the StatefulSet doesn't exist, create it. Otherwise scale it safely
            logger.debug("Creating/replacing namespaced StatefulSet.");
            K8sResourceUtils.createOrReplaceResource(
                    () -> {
                        appsApi.createNamespacedStatefulSet(statefulSet.getMetadata().getNamespace(), statefulSet, null, null, null);
                        logger.info("Created namespaced StatefulSet.");
                    },
                    () -> replaceStatefulSet(statefulSet)
            );
        }
    }

    private V1Container fileLimitInit() {
        return new V1Container()
                .securityContext(new V1SecurityContext().privileged(dataCenterSpec.getPrivilegedSupported()))
                .name("sidecar-file-limits")
                .image(dataCenterSpec.getSidecarImage())
                .imagePullPolicy(dataCenterSpec.getImagePullPolicy())
                .command(ImmutableList.of("bash", "-c", "sysctl -w vm.max_map_count=1048575 || true"));
    }


    private static void configMapVolumeAddFile(final V1ConfigMap configMap, final V1ConfigMapVolumeSource volumeSource, final String path, final String content) {
        final String encodedKey = path.replaceAll("\\W", "_");

        configMap.putDataItem(encodedKey, content);
        volumeSource.addItemsItem(new V1KeyToPath().key(encodedKey).path(path));
    }


    private static String toYamlString(final Object object) {
        return new Yaml().dump(object);
    }

    private static final long MB = 1024 * 1024;
    private static final long GB = MB * 1024;

    private ConfigMapVolumeMount createOrReplaceOperatorConfigMap(final V1Service seedNodesService) throws IOException, ApiException {
        final V1ConfigMap configMap = new V1ConfigMap()
                .metadata(dataCenterChildObjectMetadata("%s-operator-config"));

        final V1ConfigMapVolumeSource volumeSource = new V1ConfigMapVolumeSource().name(configMap.getMetadata().getName());

        // cassandra.yaml overrides
        {
            final Map<String, Object> config = new HashMap<>(); // can't use ImmutableMap as some values are null

            config.put("cluster_name", dataCenterMetadata.getName()); // TODO: support multi-DC & cluster names

            config.put("listen_address", null); // let C* discover the listen address
            config.put("rpc_address", null); // let C* discover the rpc address

            // messy -- constructs via org.apache.cassandra.config.ParameterizedClass.ParameterizedClass(java.util.Map<java.lang.String,?>)
            config.put("seed_provider", ImmutableList.of(ImmutableMap.of(
                            "class_name", "com.instaclustr.cassandra.k8s.SeedProvider",
                            "parameters", ImmutableList.of(ImmutableMap.of("service", seedNodesService.getMetadata().getName()))
                    )));


            config.put("endpoint_snitch", "org.apache.cassandra.locator.GossipingPropertyFileSnitch");


            configMapVolumeAddFile(configMap, volumeSource, "cassandra.yaml.d/001-operator-overrides.yaml", toYamlString(config));
        }

        // GossipingPropertyFileSnitch config
        {
            final Properties rackDcProperties = new Properties();

            rackDcProperties.setProperty("dc", dataCenterMetadata.getName());
            rackDcProperties.setProperty("rack", "the-rack"); // TODO: support multiple racks - Can't proceed until https://github.com/kubernetes/kubernetes/issues/41598 is fixed
            rackDcProperties.setProperty("prefer_local", "true"); // TODO: support multiple racks

            final StringWriter writer = new StringWriter();
            rackDcProperties.store(writer, "generated by cassandra-operator");

            configMapVolumeAddFile(configMap, volumeSource, "cassandra-rackdc.properties", writer.toString());
        }

        // prometheus support
        if (dataCenterSpec.getPrometheusSupport()) {
            configMapVolumeAddFile(configMap, volumeSource, "cassandra-env.sh.d/001-cassandra-exporter.sh",
                    "JVM_OPTS=\"${JVM_OPTS} -javaagent:${CASSANDRA_HOME}/agents/cassandra-exporter-agent.jar=@${CASSANDRA_CONF}/cassandra-exporter.conf\"");
        }

        // tune ulimits
        configMapVolumeAddFile(configMap, volumeSource,  "cassandra-env.sh.d/002-cassandra-limits.sh",
                "ulimit -l unlimited\n" // unlimited locked memory
        );

        // heap size and GC settings
        // TODO: tune
        {
            final long coreCount = 4; // TODO: not hard-coded
            final long memoryLimit = dataCenterSpec.getResources().getLimits().get("memory").getNumber().longValue();

            // same as stock cassandra-env.sh
            final long jvmHeapSize = Math.max(
                    Math.min(memoryLimit / 2, 1 * GB),
                    Math.min(memoryLimit / 4, 8 * GB)
            );

            final long youngGenSize = Math.min(
                    MB * coreCount,
                    jvmHeapSize / 4
            );

            final boolean useG1GC = (jvmHeapSize > 8 * GB);

            final StringWriter writer = new StringWriter();
            try (final PrintWriter printer = new PrintWriter(writer)) {
                printer.format("-Xms%d%n", jvmHeapSize); // min heap size
                printer.format("-Xmx%d%n", jvmHeapSize); // max heap size

                // copied from stock jvm.options
                if (!useG1GC) {
                    printer.format("-Xmn%d%n", youngGenSize); // young gen size

                    printer.println("-XX:+UseParNewGC");
                    printer.println("-XX:+UseConcMarkSweepGC");
                    printer.println("-XX:+CMSParallelRemarkEnabled");
                    printer.println("-XX:SurvivorRatio=8");
                    printer.println("-XX:MaxTenuringThreshold=1");
                    printer.println("-XX:CMSInitiatingOccupancyFraction=75");
                    printer.println("-XX:+UseCMSInitiatingOccupancyOnly");
                    printer.println("-XX:CMSWaitDuration=10000");
                    printer.println("-XX:+CMSParallelInitialMarkEnabled");
                    printer.println("-XX:+CMSEdenChunksRecordAlways");
                    printer.println("-XX:+CMSClassUnloadingEnabled");

                } else {
                    printer.println("-XX:+UseG1GC");
                    printer.println("-XX:G1RSetUpdatingPauseTimePercent=5");
                    printer.println("-XX:MaxGCPauseMillis=500");

                    if (jvmHeapSize > 16 * GB) {
                        printer.println("-XX:InitiatingHeapOccupancyPercent=70");
                    }

                    // TODO: tune -XX:ParallelGCThreads, -XX:ConcGCThreads
                }

                // OOM Error handling
                printer.println("-XX:+HeapDumpOnOutOfMemoryError");
                printer.println("-XX:+CrashOnOutOfMemoryError");
            }

            configMapVolumeAddFile(configMap, volumeSource, "jvm.options.d/001-jvm-memory-gc.options", writer.toString());
        }

        // TODO: maybe tune -Dcassandra.available_processors=number_of_processors - Wait till we build C* for Java 11
        // not sure if k8s exposes the right number of CPU cores inside the container


        k8sResourceUtils.createOrReplaceNamespacedConfigMap(configMap);

        return new ConfigMapVolumeMount("operator-config-volume", "/tmp/operator-config", volumeSource);
    }

    private V1Service createOrReplaceSeedNodesService() throws ApiException {
        final V1ObjectMeta serviceMetadata = dataCenterChildObjectMetadata("%s-seeds")
                // tolerate-unready-endpoints - allow the seed provider can discover the other seeds (and itself) before the readiness-probe gives the green light
                .putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");

        final V1Service service = new V1Service()
                .metadata(serviceMetadata)
                .spec(new V1ServiceSpec()
                        .publishNotReadyAddresses(true)
                        .clusterIP("None")
                        // a port needs to be defined for the service to be resolvable (#there-was-a-bug-ID-and-now-I-cant-find-it)
                        .ports(ImmutableList.of(new V1ServicePort().name("internode").port(7000)))
                        .selector(dataCenterLabels)
                );

        k8sResourceUtils.createOrReplaceNamespacedService(service);

        return service;
    }

    private void createOrReplaceNodesService() throws ApiException {
        final V1ObjectMeta serviceMetadata = dataCenterChildObjectMetadata("%s-nodes");

        final V1Service service = new V1Service()
                .metadata(serviceMetadata)
                .spec(new V1ServiceSpec()
                        .clusterIP("None")
                        .addPortsItem(new V1ServicePort().name("cql").port(9042))
                        .addPortsItem(new V1ServicePort().name("jmx").port(7199))
                        .selector(dataCenterLabels)
                );

        if (dataCenterSpec.getPrometheusSupport()) {
            service.getSpec().addPortsItem(new V1ServicePort().name("prometheus").port(9500));
        }

        k8sResourceUtils.createOrReplaceNamespacedService(service);
    }

    private static final Pattern STATEFUL_SET_POD_NAME_PATTERN = Pattern.compile(".*-(?<index>\\d+)");

    // comparator comparing StatefulSet Pods based on their names (which include an index)
    // "newest" pod first.
    private static final Comparator<V1Pod> STATEFUL_SET_POD_NEWEST_FIRST_COMPARATOR = Comparator.comparingInt((V1Pod p) -> {
        final String podName = p.getMetadata().getName();
        final Matcher matcher = STATEFUL_SET_POD_NAME_PATTERN.matcher(podName);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("Pod %s name doesn't match expression %s", podName, STATEFUL_SET_POD_NAME_PATTERN));
        }

        return Integer.valueOf(matcher.group("index"));
    }).reversed();

    private static final ImmutableSet<Status.OperationMode> SCALE_UP_OPERATION_MODES = Sets.immutableEnumSet(Status.OperationMode.NORMAL);
    private static final ImmutableSet<Status.OperationMode> SCALE_DOWN_OPERATION_MODES = Sets.immutableEnumSet(Status.OperationMode.NORMAL, Status.OperationMode.DECOMMISSIONED);

    private void replaceStatefulSet(final V1beta2StatefulSet statefulSet) throws ApiException {
        final V1ObjectMeta statefulSetMetadata = statefulSet.getMetadata();

        final V1beta2StatefulSet existingStatefulSet = appsApi.readNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), null, null, null);

        final int existingSpecReplicas = existingStatefulSet.getSpec().getReplicas();
        final int desiredSpecReplicas = dataCenterSpec.getReplicas();

        // check that the existing StatefulSet is in a stable state and not undergoing scaling
        {
            final int currentReplicas = existingStatefulSet.getStatus().getReplicas(); // number of created pods

            logger.debug("StatefulSet existingSpecReplicas = {}, desiredSpecReplicas = {}, currentReplicas = {}.", existingSpecReplicas, desiredSpecReplicas, currentReplicas);

            if (currentReplicas != existingSpecReplicas) {
                // some pods have not yet been created or destroyed
                logger.warn("Skipping StatefulSet reconciliation as it is undergoing scaling operations.");

                return;
            }
        }


        // ideally this should use the same selector as the StatefulSet.
        // why does listNamespacedPod take a string for the selector when V1LabelSelector exists?
        final String labelSelector = LabelSelectors.equalitySelector(OperatorLabels.DATACENTER, dataCenterMetadata.getName());

        final List<V1Pod> pods = ImmutableList.sortedCopyOf(STATEFUL_SET_POD_NEWEST_FIRST_COMPARATOR,
                k8sResourceUtils.listNamespacedPods(statefulSetMetadata.getNamespace(), null, labelSelector)
        );

        logger.debug("Found {} pods.", pods.size());



        // check that all pods are running (note that Running != Ready)
        {
            final Multimap<String, V1Pod> podsByPhase = Multimaps.index(pods, pod -> pod.getStatus().getPhase());
            final Multimap<String, V1Pod> notRunningPodsByPhase = Multimaps.filterKeys(podsByPhase, k -> !k.equals("Running"));

            if (notRunningPodsByPhase.size() > 0) {
                logger.warn("Skipping StatefulSet reconciliation as some Pods are not in the Running phase: {}.",
                    Multimaps.transformValues(notRunningPodsByPhase, (V1Pod p) -> p.getMetadata().getName())
                );

                return;
            }
        }


        final Map<V1Pod, SidecarClient> podClients = Maps.toMap(pods, sidecarClientFactory::clientForPod);
        final Map<V1Pod, Future<Status>> podCassandraStatuses = ImmutableMap.copyOf(Maps.transformValues(podClients, SidecarClient::status));

        final Multimap<Status.OperationMode, V1Pod> podsByCassandraOperationMode;
        {
            final ImmutableMultimap.Builder<Status.OperationMode, V1Pod> builder = ImmutableMultimap.builder();
            boolean podFailures = false;

            for (final Map.Entry<V1Pod, Future<Status>> entry : podCassandraStatuses.entrySet()) {
                final V1Pod pod = entry.getKey();

                try {
                    final Status.OperationMode operationMode = entry.getValue().get().operationMode;

                    builder.put(operationMode, pod);

                } catch (final Exception e) {
                    logger.error("Failed to get the status of Cassandra Pod {}.", pod.getMetadata().getName(), e);
                    podFailures = true;
                }
            }

            if (podFailures) {
                logger.warn("Skipping StatefulSet reconciliation as the status of some Cassandra nodes could not be queried.");
                return;
            }

            podsByCassandraOperationMode = builder.build();
        }


        // check that all Cassandra nodes are in the right state
        // TODO: extend this to a full "health check"
        {
            final Multimap<Status.OperationMode, V1Pod> incorrectStatePodsByCassandraOperationMode = Multimaps.filterKeys(podsByCassandraOperationMode, mode -> {
                if (desiredSpecReplicas >= existingSpecReplicas) {
                    return !SCALE_UP_OPERATION_MODES.contains(mode);

                } else {
                    return !SCALE_DOWN_OPERATION_MODES.contains(mode);
                }
            });

            if (incorrectStatePodsByCassandraOperationMode.size() > 0) {
                logger.warn("Skipping StatefulSet reconciliation as some Cassandra Pods are not in the correct mode: {}.",
                        Multimaps.transformValues(incorrectStatePodsByCassandraOperationMode, (V1Pod p) -> p.getMetadata().getName())
                );

                return;
            }
        }


        if (desiredSpecReplicas > existingSpecReplicas) {
            logger.debug("Scaling StatefulSet up.");

            existingStatefulSet.getSpec().setReplicas(existingSpecReplicas + 1);
            appsApi.replaceNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), existingStatefulSet, null, null);

        } else if (desiredSpecReplicas < existingSpecReplicas) {
            // if all nodes are NORMAL, kick off a decommission
            // if all nodes except the "newest" are NORMAL, and the newest is DECOMMISSIONED, scale the StatefulSet

            final V1Pod newestPod = pods.get(0);

            final Collection<V1Pod> decommissionedPods = podsByCassandraOperationMode.get(Status.OperationMode.DECOMMISSIONED);

            if (decommissionedPods.isEmpty()) {
                logger.debug("No Cassandra nodes have been decommissioned. Decommissioning the newest node.");

                sidecarClientFactory.clientForPod(newestPod).decommission();

            } else if (decommissionedPods.size() == 1) {
                logger.debug("Decommissioned Cassandra node found. Scaling StatefulSet down.");

                final V1Pod decommissionedPod = Iterables.getOnlyElement(podsByCassandraOperationMode.get(Status.OperationMode.DECOMMISSIONED));

                if (decommissionedPod != newestPod) {
                    logger.error("Skipping StatefulSet reconciliation as the DataCenter contains one decommissioned Cassandra node, but it isn't the newest. Decommissioned Pod = {}, expecting Pod = {}.",
                            decommissionedPod.getMetadata().getName(), newestPod.getMetadata().getName());

                    return;
                }

                existingStatefulSet.getSpec().setReplicas(existingSpecReplicas - 1);
                appsApi.replaceNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), existingStatefulSet, null, null);

                // TODO: this is disabled for now for safety. The operator will delete all PVCs on deletion of the cluster for now.
//                k8sResourceUtils.deletePersistentVolumeAndPersistentVolumeClaim(decommissionedPod);
            } else {
                logger.error("Skipping StatefulSet reconciliation as the DataCenter contains more than one decommissioned Cassandra node: {}.",
                        Iterables.transform(decommissionedPods, (V1Pod p) -> p.getMetadata().getName()));
            }

        } else {

            appsApi.replaceNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), statefulSet, null, null);
            logger.debug("Replaced namespaced StatefulSet.");
        }

    }

    private void createOrReplacePrometheusServiceMonitor() throws ApiException {
        final String name = dataCenterChildObjectName("%s");

        final ImmutableMap<String, Object> prometheusServiceMonitor = ImmutableMap.<String, Object>builder()
                .put("apiVersion", "monitoring.coreos.com/v1")
                .put("kind", "ServiceMonitor")
                .put("metadata", ImmutableMap.<String, Object>builder()
                        .put("name", name)
                        .put("labels", ImmutableMap.<String, Object>builder()
                                .putAll(dataCenterLabels)
                                .putAll(Optional.ofNullable(dataCenterSpec.getPrometheusServiceMonitorLabels()).orElse(ImmutableMap.of()))
                                .build()
                        )
                        .build()
                )
                .put("spec", ImmutableMap.<String, Object>builder()
                        .put("selector", ImmutableMap.<String, Object>builder()
                                .put("matchLabels", ImmutableMap.<String, Object>builder()
                                        .putAll(dataCenterLabels)
                                        .build()
                                )
                                .build()
                        )
                        .put("endpoints", ImmutableList.<Map<String, Object>>builder()
                                .add(ImmutableMap.<String, Object>builder()
                                        .put("port", "prometheus")
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();

        K8sResourceUtils.createOrReplaceResource(
                () -> customObjectsApi.createNamespacedCustomObject("monitoring.coreos.com", "v1", dataCenterMetadata.getNamespace(), "servicemonitors", prometheusServiceMonitor, null),
                () -> customObjectsApi.replaceNamespacedCustomObject("monitoring.coreos.com", "v1", dataCenterMetadata.getNamespace(), "servicemonitors", name, prometheusServiceMonitor)
        );
    }
}
