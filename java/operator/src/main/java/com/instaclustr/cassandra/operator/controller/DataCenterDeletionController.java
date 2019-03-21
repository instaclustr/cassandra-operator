package com.instaclustr.cassandra.operator.controller;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;

public class DataCenterDeletionController {
    private final CoreV1Api coreApi;
    private final AppsV1beta2Api appsApi;
    private final K8sResourceUtils k8sResourceUtils;

    private final DataCenterKey dataCenterKey;

    @Inject
    public DataCenterDeletionController(final CoreV1Api coreApi,
                                        final AppsV1beta2Api appsApi,
                                        final K8sResourceUtils k8sResourceUtils,
                                        @Assisted final DataCenterKey dataCenterKey) {
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        this.k8sResourceUtils = k8sResourceUtils;
        this.dataCenterKey = dataCenterKey;
    }

    public void deleteDataCenter() throws Exception {
        final String labelSelector = String.format("cassandra-datacenter=%s", dataCenterKey.name);

//        // delete persistent volumes & persistent volume claims
//        final V1PodList pods = coreApi.listNamespacedPod(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, null, null);
//        for (final V1Pod pod : pods.getItems()) {
//            k8sResourceUtils.deletePersistentVolumeAndPersistentVolumeClaim(pod);
//        }

        // delete statefulset
        final V1beta2StatefulSetList statefulSets = appsApi.listNamespacedStatefulSet(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1beta2StatefulSet statefulSet : statefulSets.getItems()) {
            k8sResourceUtils.deleteStatefulSet(statefulSet);
        }

        // delete configmap
        final V1ConfigMapList configMaps = coreApi.listNamespacedConfigMap(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1ConfigMap configMap : configMaps.getItems()) {
            k8sResourceUtils.deleteConfigMap(configMap);
        }

        // delete services
        final V1ServiceList services = coreApi.listNamespacedService(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1Service service : services.getItems()) {
            k8sResourceUtils.deleteService(service);
        }
    }
}
