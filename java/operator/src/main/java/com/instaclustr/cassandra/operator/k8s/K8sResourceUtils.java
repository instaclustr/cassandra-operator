package com.instaclustr.cassandra.operator.k8s;

import com.google.common.collect.ImmutableList;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

import static com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport.putNamespacedName;

public class K8sResourceUtils {
    private static final Logger logger = LoggerFactory.getLogger(K8sResourceUtils.class);

    private final ApiClient apiClient;
    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;

    @Inject
    public K8sResourceUtils(final ApiClient apiClient,
                            final CoreV1Api coreApi,
                            final AppsV1beta2Api appsApi) {
        this.apiClient = apiClient;
        this.coreApi = coreApi;
        this.appsApi = appsApi;
    }

    @FunctionalInterface
    public interface ApiCallable {
        void call() throws ApiException;
    }

    public static void createOrReplaceResource(final ApiCallable createResourceCallable, final ApiCallable replaceResourceCallable) throws ApiException {
        try {
            logger.trace("Attempting to create resource.");
            createResourceCallable.call();

        } catch (final ApiException e) {
            if (e.getCode() != 409)
                throw e;

            logger.trace("Resource already exists. Attempting to replace.");
            replaceResourceCallable.call();
        }
    }

    public void createOrReplaceNamespacedService(final V1Service service) throws ApiException {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _serviceMDC = putNamespacedName("Service", service.getMetadata())) {
            final String namespace = service.getMetadata().getNamespace();

            logger.debug("Creating/replacing namespaced Service.");

            createOrReplaceResource(
                    () -> {
                        coreApi.createNamespacedService(namespace, service, null, null, null);
                        logger.debug("Created namespaced Service.");
                    },
                    //coreApi.replaceNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), service, null)
                    // temporarily disable service replace call to fix issue #41 since service can't be customized right now
                    () -> {}
            );
        }
    }

    public void createOrReplaceNamespacedConfigMap(final V1ConfigMap configMap) throws ApiException {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _configMapMDC = putNamespacedName("ConfigMap", configMap.getMetadata())) {
            final String namespace = configMap.getMetadata().getNamespace();

            logger.debug("Creating/replacing namespaced ConfigMap.");

            createOrReplaceResource(
                    () -> {
                        coreApi.createNamespacedConfigMap(namespace, configMap, null, null, null);
                        logger.debug("Created namespaced ConfigMap.");
                    },
                    () -> {
                        coreApi.replaceNamespacedConfigMap(configMap.getMetadata().getName(), namespace, configMap, null, null);
                        logger.debug("Replaced namespaced ConfigMap.");
                    }
            );
        }
    }

    public void deleteService(final V1Service service) throws ApiException {
        final V1ObjectMeta metadata = service.getMetadata();

        coreApi.deleteNamespacedService(metadata.getName(), metadata.getNamespace(), null, null, null, null, null, null);
    }

    public void deleteConfigMap(final V1ConfigMap configMap) throws ApiException {
        final V1ObjectMeta configMapMetadata = configMap.getMetadata();

        coreApi.deleteNamespacedConfigMap(configMapMetadata.getName(), configMapMetadata.getNamespace(), null, null, null, null, null, null);
    }

    public void deleteStatefulSet(final V1beta2StatefulSet statefulSet) throws ApiException {
        V1DeleteOptions deleteOptions = new V1DeleteOptions()
                .propagationPolicy("Foreground");


//        //Scale the statefulset down to zero (https://github.com/kubernetes/client-go/issues/91)
//        statefulSet.getSpec().setReplicas(0);
//
//        appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), statefulSet, null, null);
//
//        while (true) {
//            int currentReplicas = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), null, null, null).getStatus().getReplicas();
//            if (currentReplicas == 0)
//                break;
//
//            Thread.sleep(50);
//        }
//
//        logger.debug("done with scaling to 0");

        final V1ObjectMeta statefulSetMetadata = statefulSet.getMetadata();

        appsApi.deleteNamespacedStatefulSet(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), deleteOptions, null, null, null, false, "Foreground");
    }

    public void deletePersistentVolumeAndPersistentVolumeClaim(final V1Pod pod) throws ApiException {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _podMDC = putNamespacedName("Pod", pod.getMetadata())) {
            logger.debug("Deleting Pod Persistent Volumes and Claims.");

            final V1DeleteOptions deleteOptions = new V1DeleteOptions()
                    .propagationPolicy("Foreground");

            // TODO: maybe delete all volumes?
            final String pvcName = pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
            final V1PersistentVolumeClaim pvc = coreApi.readNamespacedPersistentVolumeClaim(pvcName, pod.getMetadata().getNamespace(), null, null, null);

            coreApi.deleteNamespacedPersistentVolumeClaim(pvcName, pod.getMetadata().getNamespace(), deleteOptions, null, null, null, null, null);
            coreApi.deletePersistentVolume(pvc.getSpec().getVolumeName(), deleteOptions, null, null, null, null, null);
        }
    }

    public List<V1Pod> listNamespacedPods(final String namespace, final String fieldSelector, final String labelSelector) throws ApiException {
        final ImmutableList.Builder<V1Pod> listBuilder = ImmutableList.builder();

        String continueToken = null;

        do {
            final V1PodList podList = coreApi.listNamespacedPod(namespace, null, null, continueToken, fieldSelector, labelSelector, null, null, null, null);

            listBuilder.addAll(podList.getItems());

            continueToken = podList.getMetadata().getContinue();
        } while (continueToken != null);

        return listBuilder.build();
    }
}
