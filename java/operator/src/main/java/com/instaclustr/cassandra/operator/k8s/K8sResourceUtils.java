package com.instaclustr.cassandra.operator.k8s;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class K8sResourceUtils {
    private static final Logger logger = LoggerFactory.getLogger(K8sResourceUtils.class);

    private final ApiClient apiClient;
    private final CoreV1Api coreApi;

    @Inject
    public K8sResourceUtils(final ApiClient apiClient,
                            final CoreV1Api coreApi) {
        this.apiClient = apiClient;
        this.coreApi = coreApi;
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
                coreApi.createNamespacedServiceCall(service.getMetadata().getNamespace(), service, null, null, null),
                //coreApi.replaceNamespacedServiceCall(service.getMetadata().getName(), service.getMetadata().getNamespace(), service, null, null, null)
                // temporarily disable service replace call to fix issue #41 since service can't be customized right now
                coreApi.readNamespacedServiceCall(service.getMetadata().getName(), service.getMetadata().getNamespace(), null, null, null, null, null)
        );
    }

    public void createOrReplaceNamespaceConfigMap(final V1ConfigMap configMap) throws ApiException {
        final String namespace = configMap.getMetadata().getNamespace();

        createOrReplaceResource(
                coreApi.createNamespacedConfigMapCall(namespace, configMap, null, null, null),
                coreApi.replaceNamespacedConfigMapCall(configMap.getMetadata().getName(), namespace, configMap, null, null, null)
        );
    }
}
