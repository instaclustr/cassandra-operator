package com.instaclustr.cassandra.operator.controller;

import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataCenterDeletionController {

    private static final Logger logger = LoggerFactory.getLogger(DataCenterDeletionController.class);

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
        final String labelSelector = String.format("%s=%s,%s", OperatorLabels.DATACENTER, dataCenterKey.name, "app.kubernetes.io/managed-by=com.instaclustr.cassandra-operator");


        // delete persistent volumes & persistent volume claims
        // TODO: this is disabled for now for safety. Perhaps add a flag or something to control this.
//        final V1PodList pods = coreApi.listNamespacedPod(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, null, null);
//        for (final V1Pod pod : pods.getItems()) {
//            k8sResourceUtils.deletePersistentVolumeAndPersistentVolumeClaim(pod);
//        }

        // delete StatefulSets
        final V1beta2StatefulSetList statefulSets = appsApi.listNamespacedStatefulSet(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1beta2StatefulSet statefulSet : statefulSets.getItems()) {
            try {
                k8sResourceUtils.deleteStatefulSet(statefulSet);
            } catch (JsonSyntaxException e) {
                logger.debug("Caught JSON exception while deleting statefulSet, ignoring due to https://github.com/kubernetes-client/java/issues/86");
            }
        }

        // delete ConfigMaps
        final V1ConfigMapList configMaps = coreApi.listNamespacedConfigMap(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1ConfigMap configMap : configMaps.getItems()) {
            try {
                k8sResourceUtils.deleteConfigMap(configMap);
            } catch (JsonSyntaxException e) {
                logger.debug("Caught JSON exception while deleting configMap, ignoring due to https://github.com/kubernetes-client/java/issues/86");
            }
        }

        // delete Services
        final V1ServiceList services = coreApi.listNamespacedService(dataCenterKey.namespace, null, null, null, null, labelSelector, null, null, 30, null);
        for (final V1Service service : services.getItems()) {
            try {
                k8sResourceUtils.deleteService(service);
            } catch (JsonSyntaxException e) {
                logger.debug("Caught JSON exception while deleting service, ignoring due to https://github.com/kubernetes-client/java/issues/86");
            }
        }
    }
}
