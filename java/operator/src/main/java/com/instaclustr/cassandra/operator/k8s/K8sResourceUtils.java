package com.instaclustr.cassandra.operator.k8s;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsApi;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

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
            createResourceCallable.call();

        } catch (final ApiException e) {
            if (e.getCode() != 409)
                throw e;

            try {
                replaceResourceCallable.call();
            } catch (ApiException e1) {
                logger.warn("Failed to update resource. This will be a hard exception in the future.", e1);
            }
        }
    }

    public void createOrReplaceResource(final Call createResourceCall, final Call replaceResourceCall) throws ApiException {
        createOrReplaceResource(
                () -> apiClient.execute(createResourceCall),
                () -> apiClient.execute(replaceResourceCall)
        );
    }

    public void deleteResource(final Call deleteResourceCall) throws ApiException {
        apiClient.execute(deleteResourceCall);
    }

    public void createOrReplaceNamespaceService(final V1Service service) throws ApiException {
        createOrReplaceResource(
                coreApi.createNamespacedServiceCall(service.getMetadata().getNamespace(), service, null, null, null, null, null),
                //coreApi.replaceNamespacedServiceCall(service.getMetadata().getName(), service.getMetadata().getNamespace(), service, null, null, null)
                // temporarily disable service replace call to fix issue #41 since service can't be customized right now
                coreApi.readNamespacedServiceCall(service.getMetadata().getName(), service.getMetadata().getNamespace(), null, null, null, null, null)
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

    public void deleteConfigMap(final V1ConfigMap configMap, final V1DeleteOptions deleteOptions) throws Exception {
        final V1ObjectMeta configMapMetadata = configMap.getMetadata();

        deleteResource(
                coreApi.deleteNamespacedConfigMapCall(configMapMetadata.getName(), configMapMetadata.getNamespace(), deleteOptions, null, null, null, null, null, null, null)
        );
    }

    public void deleteStatefulSet(final V1beta2StatefulSet statefulSet) throws Exception {
        V1DeleteOptions deleteOptions = new V1DeleteOptions()
                .propagationPolicy("Foreground");


        //Scale the statefulset down to zero (https://github.com/kubernetes/client-go/issues/91)
        statefulSet.getSpec().setReplicas(0);

        appsApi.replaceNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), statefulSet, null, null);

        while (true) {
            int currentReplicas = appsApi.readNamespacedStatefulSet(statefulSet.getMetadata().getName(), statefulSet.getMetadata().getNamespace(), null, null, null).getStatus().getReplicas();
            if (currentReplicas == 0)
                break;

            Thread.sleep(50);
        }

        logger.debug("done with scaling to 0");

        final V1ObjectMeta statefulSetMetadata = statefulSet.getMetadata();

        deleteResource(appsApi.deleteNamespacedStatefulSetCall(statefulSetMetadata.getName(), statefulSetMetadata.getNamespace(), deleteOptions, null, null, null, false, "Foreground", null, null));
    }

    public void deletePersistentVolumeAndPersistentVolumeClaim(final V1Pod pod) throws Exception {
        final V1DeleteOptions deleteOptions = new V1DeleteOptions()
                .propagationPolicy("Foreground");

        final String pvcName = pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
        final V1PersistentVolumeClaim pvc = coreApi.readNamespacedPersistentVolumeClaim(pvcName, pod.getMetadata().getNamespace(), null, null, null);

        deleteResource(coreApi.deleteNamespacedPersistentVolumeClaimCall(pvcName, pod.getMetadata().getNamespace(), deleteOptions, null, null, null, null, null, null, null));
        deleteResource(coreApi.deletePersistentVolumeCall(pvc.getSpec().getVolumeName(), deleteOptions, null, null, null, null, null, null, null));
    }
}
