package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Resources;
import com.google.common.net.InetAddresses;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.gson.JsonSyntaxException;
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
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
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
			// processNextDataCenterEvent();
			final DataCenterKey dataCenterKey = dataCenterQueue.take();
			if (dataCenterKey == POISON)
				return;
			reconcileDataCenter(dataCenterKey);

			// processNextClusterEvent();
			// processNextSecretEvent();
			// processNextStatefulSetEvent();
		}
	}

	// reconcile DataCenter current status with desired status stored in
	// DataCenterCache
	public void reconcileDataCenter(DataCenterKey dataCenterKey) throws Exception {
		final DataCenter dataCenter = dataCenterCache.getIfPresent(dataCenterKey);

		// DELETED 
		if (dataCenter == null) {
			deleteDataCenter(dataCenterKey);
			return;
		}

		// ADDED & MODIFIED (Currently only support SCALE-UP)
		createOrReplaceDataCenter(dataCenterKey);
	}

	private void createOrReplaceDataCenter(DataCenterKey dataCenterKey) throws Exception {   	
		// create the public service (what clients use to discover the data center)
		V1Service publicService = generatePublicService(dataCenterKey);
		createOrReplaceNamespaceService(publicService);

		// create the seed-node service (what C* nodes use to discover the DC seeds)
		V1Service seednodeService = generateSeednodeService(dataCenterKey);
		createOrReplaceNamespaceService(seednodeService);

		// create configmap
		V1ConfigMap configMap = generateConfigMap(dataCenterKey);
		createOrReplaceNamespaceConfigMap(configMap);

		// create the statefulset for the DC nodes
		V1beta2StatefulSet statefulSet = generateStatefulSet(dataCenterKey, configMap);
		createOrReplaceNamespaceStatefulSet(statefulSet);
	}

	private void deleteDataCenter(DataCenterKey dataCenterKey) throws Exception {
		String labelSelector = String.format("cassandra-datacenter=%s", dataCenterKey.name);

		// delete statefulset
		V1beta2StatefulSetList statefulSets = appsApi.listNamespacedStatefulSet(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
		for (V1beta2StatefulSet statefulSet : statefulSets.getItems()) {
			deleteStatefulSet(statefulSet);
		}

		// delete configmap
		V1ConfigMapList configMaps = coreApi.listNamespacedConfigMap(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
		for (V1ConfigMap configMap : configMaps.getItems()) {
			deleteConfigMap(configMap);
		}

		// delete services
		V1ServiceList services = coreApi.listNamespacedService(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
		for (V1Service service : services.getItems()) {
			deleteService(service);
		}
	}



	@FunctionalInterface
	private interface ApiCallable {
		void call() throws ApiException;
	}

	private V1Service generatePublicService(DataCenterKey dataCenterKey) {
		final V1Service publicService = new V1Service()
				.metadata(new V1ObjectMeta()
						.name(dataCenterKey.name)
						.labels(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
						)
				.spec(new V1ServiceSpec()
						.clusterIP("None")
						.ports(ImmutableList.of(new V1ServicePort().name("cql").port(9042)))
						.selector(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
						);

		return publicService;
	}

	private V1Service generateSeednodeService(DataCenterKey dataCenterKey) {
		final V1Service seednodeService = new V1Service()
				.metadata(new V1ObjectMeta()
						.name(String.format("%s-seeds", dataCenterKey.name))
						.labels(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
						.putAnnotationsItem("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true")
						)
				.spec(new V1ServiceSpec()
						.publishNotReadyAddresses(true)
						.clusterIP("None")
						// a port needs to be defined for the service to be resolvable (#there-was-a-bug-ID-and-now-I-cant-find-it)
						.ports(ImmutableList.of(new V1ServicePort().name("internode").port(7000)))
						.selector(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
						);

		return seednodeService;
	}

	private V1ConfigMap generateConfigMap(DataCenterKey dataCenterKey) throws IOException {
		final V1ConfigMap configMap = new V1ConfigMap()
				.metadata(new V1ObjectMeta()
						.name(String.format("%s-config", dataCenterKey.name))
						.labels(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name)))
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

		return configMap;
	}

	private V1beta2StatefulSet generateStatefulSet(DataCenterKey dataCenterKey, V1ConfigMap configMap) {
		final DataCenter dataCenter = dataCenterCache.getIfPresent(dataCenterKey);

		int replicas = 0;
		try {
			replicas = dataCenter.getSpec().getReplicas().intValue();
		} catch (Exception e) {
			logger.debug("Invalid Replica Number: {}", dataCenter.getSpec().getReplicas());
			throw e;
		}

		final V1beta2StatefulSet statefulSet = new V1beta2StatefulSet()
				.metadata(new V1ObjectMeta()
						.name(dataCenterKey.name)
						.labels(ImmutableMap.of("cassandra-datacenter", dataCenterKey.name))
						)
				.spec(new V1beta2StatefulSetSpec()
						.serviceName("cassandra")
						.replicas(replicas)
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
												.resources(new V1ResourceRequirements()
														.putLimitsItem("memory", Quantity.fromString("512Mi"))
														.putRequestsItem("memory", Quantity.fromString("512Mi"))
														)
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

		return statefulSet;
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
				() -> replaceStatefulSet(statefulSet)
				);
	}

	private void replaceStatefulSet(final V1beta2StatefulSet statefulSet) throws ApiException {
		Integer currentReplicas = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, null, null, null).getSpec().getReplicas();
		Integer desiredReplicas = statefulSet.getSpec().getReplicas();

		//logger.debug("current replicas: {}", currentReplicas);
		//logger.debug("desired replicas: {}", desiredReplicas);

		// SCALE-DOWN
		if (desiredReplicas < currentReplicas) {
			logger.debug("SCALE-DOWN is under implementing.");
			final V1PodList podList = coreApi.listNamespacedPod(namespace, null, null, null, null, "cassandra-datacenter=test-dc", null, null, null, null);

			// sort pods in a reverse order
			podList.getItems().sort(new Comparator<V1Pod>() {
				public int compare(V1Pod pod1, V1Pod pod2) {
					String pod1Name = pod1.getMetadata().getName();
					int index1 = Integer.parseInt(pod1Name.split("-")[pod1Name.split("-").length-1]);
					String pod2Name = pod2.getMetadata().getName();
					int index2 = Integer.parseInt(pod2Name.split("-")[pod2Name.split("-").length-1]);

					return index2 - index1;
				}
			});


			for (int i = 0; i < currentReplicas - desiredReplicas; i++) {
				V1Pod pod = podList.getItems().get(i);
				final InetAddress podIp = InetAddresses.forString(pod.getStatus().getPodIP());
				logger.debug("pod {} has IP {}", pod.getMetadata().getName(), podIp);
				// TODO: decommission current cassandra node inside this pod through JMX call
			}
		}

		appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, statefulSet, null);	
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

	private static void deleteResource(final ApiCallable deleteCallable) throws ApiException {
		try {
			deleteCallable.call();

		} catch (final ApiException e) {
			if (e.getCode() != 409)
				throw e;
		}
	}

	private void deleteStatefulSet(final V1beta2StatefulSet statefulSet) throws Exception {
		V1DeleteOptions deleteOptions = new V1DeleteOptions();
		deleteOptions.setPropagationPolicy("Foreground");

		//Scale the statefulset down to zero (https://github.com/kubernetes/client-go/issues/91)
		statefulSet.getSpec().setReplicas(0);
		try {
			appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, statefulSet, null);
		} catch (ApiException e) {
			throw e;
		}
		while (true) {    		
			Integer currentReplicas = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, null, null, null).getStatus().getReplicas();
			if (currentReplicas == 0) 
				break;

			Thread.sleep(50);
		}

		logger.debug("done with scaling to 0");

		//Catch JsonSyntaxException to avoid crash (https://github.com/kubernetes-client/java/issues/86)
		try {
			deleteResource(
					() -> appsApi.deleteNamespacedStatefulSet(statefulSet.getMetadata().getName(), namespace, deleteOptions, null, null, null, "Foreground")
					);
		}
		catch (JsonSyntaxException e) {
			if (e.getCause() instanceof IllegalStateException) {
				IllegalStateException ise = (IllegalStateException) e.getCause();
				if (ise.getMessage() != null && ise.getMessage().contains("Expected a string but was BEGIN_OBJECT"))
					logger.debug("Catching exception because of issue https://github.com/kubernetes-client/java/issues/86");
				else throw e;
			}
			else throw e;
		}

	}

	private void deleteConfigMap(final V1ConfigMap configMap) throws Exception {
		V1DeleteOptions deleteOptions = new V1DeleteOptions();

		deleteResource(
				() -> coreApi.deleteNamespacedConfigMap(configMap.getMetadata().getName(), namespace, deleteOptions, null, null, null, null)
				);
	}

	private void deleteService(final V1Service service) throws ApiException {    	
		deleteResource(
				() -> coreApi.deleteNamespacedService(service.getMetadata().getName(), namespace, null)
				);
	}

	@Override
	protected void triggerShutdown() {
		dataCenterQueue.add(POISON);
	}
}
