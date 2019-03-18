package com.instaclustr.cassandra.operator.k8s;

import com.google.common.collect.ImmutableList;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.util.List;

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

    public void createOrReplaceResource(final Call createResourceCall, final Call replaceResourceCall) throws ApiException {
        createOrReplaceResource(
                () -> apiClient.execute(createResourceCall),
                () -> apiClient.execute(replaceResourceCall)
        );
    }

    private void deleteResource(final Call deleteResourceCall) throws ApiException {
        apiClient.execute(deleteResourceCall);
    }

    public void createOrReplaceNamespaceService(final V1Service service) throws ApiException {
        final String namespace = service.getMetadata().getNamespace();

        createOrReplaceResource(
                coreApi.createNamespacedServiceCall(namespace, service, null, null, null, null, null),
                //coreApi.replaceNamespacedServiceCall(service.getMetadata().getName(), service.getMetadata().getNamespace(), service, null, null, null)
                // temporarily disable service replace call to fix issue #41 since service can't be customized right now
                coreApi.readNamespacedServiceCall(service.getMetadata().getName(), namespace, null, null, null, null, null)
        );
    }

    public void createOrReplaceNamespaceConfigMap(final V1ConfigMap configMap) throws ApiException {
        final String namespace = configMap.getMetadata().getNamespace();

        createOrReplaceResource(
                coreApi.createNamespacedConfigMapCall(namespace, configMap, null, null, null, null, null),
                coreApi.replaceNamespacedConfigMapCall(configMap.getMetadata().getName(), namespace, configMap, null, null, null, null)
        );
    }

    public void deleteService(final V1Service service) throws ApiException {
        final V1ObjectMeta metadata = service.getMetadata();

        deleteResource(
                coreApi.deleteNamespacedServiceCall(metadata.getName(), metadata.getNamespace(), null, null, null, null, null, null, null, null)
        );
    }

    public void deleteConfigMap(final V1ConfigMap configMap, final V1DeleteOptions deleteOptions) throws ApiException {
        final V1ObjectMeta configMapMetadata = configMap.getMetadata();

        deleteResource(
                coreApi.deleteNamespacedConfigMapCall(configMapMetadata.getName(), configMapMetadata.getNamespace(), deleteOptions, null, null, null, null, null, null, null)
        );
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

        deleteResource(appsApi.deleteNamespacedStatefulSetCall(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), deleteOptions, null, null, null, false, "Foreground", null, null));
    }

    public void deletePersistentVolumeAndPersistentVolumeClaim(final V1Pod pod) throws ApiException {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _podName = MDC.putCloseable("Pod", pod.getMetadata().getName());
             @SuppressWarnings("unused") final MDC.MDCCloseable _podNamespace = MDC.putCloseable("Namespace", pod.getMetadata().getNamespace())) {

            logger.debug("Deleting Persistent Volume Claim.");

            final V1DeleteOptions deleteOptions = new V1DeleteOptions()
                    .propagationPolicy("Foreground");

            final String pvcName = pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
            final V1PersistentVolumeClaim pvc = coreApi.readNamespacedPersistentVolumeClaim(pvcName, pod.getMetadata().getNamespace(), null, null, null);

            deleteResource(coreApi.deleteNamespacedPersistentVolumeClaimCall(pvcName, pod.getMetadata().getNamespace(), deleteOptions, null, null, null, null, null, null, null));
            deleteResource(coreApi.deletePersistentVolumeCall(pvc.getSpec().getVolumeName(), deleteOptions, null, null, null, null, null, null, null));
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
