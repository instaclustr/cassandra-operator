package com.instaclustr.cassandra.operator.controller;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.instaclustr.k8s.K8sLabels;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.instaclustr.cassandra.operator.k8s.K8sLoggingSupport.putNamespacedName;

public class DataCenterDeletionController {
    private static final Logger logger = LoggerFactory.getLogger(DataCenterDeletionController.class);

    private final K8sResourceUtils k8sResourceUtils;

    private final DataCenterKey dataCenterKey;

    @Inject
    public DataCenterDeletionController(final K8sResourceUtils k8sResourceUtils,
                                        @Assisted final DataCenterKey dataCenterKey) {
        this.k8sResourceUtils = k8sResourceUtils;
        this.dataCenterKey = dataCenterKey;
    }

    public void deleteDataCenter() throws Exception {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterMDC = putNamespacedName("DataCenter", dataCenterKey)) {
            logger.info("Deleting DataCenter.");

            final String labelSelector = Joiner.on(',').withKeyValueSeparator('=').join(
                    ImmutableMap.of(
                            OperatorLabels.DATACENTER, dataCenterKey.name,
                            K8sLabels.MANAGED_BY, OperatorLabels.OPERATOR_IDENTIFIER
                    )
            );

            // delete persistent volumes & persistent volume claims
            // TODO: this is disabled for now for safety. Perhaps add a flag or something to control this.
//        k8sResourceUtils.listNamespacedPods(dataCenterKey.namespace, null, labelSelector).forEach(pod -> {
//            try (@SuppressWarnings("unused") final MDC.MDCCloseable _podMDC = putNamespacedName("Pod", pod.getMetadata())) {
//                try {
//                    k8sResourceUtils.deletePersistentVolumeAndPersistentVolumeClaim(pod);
//                    logger.debug("Deleted Pod Persistent Volume & Claim.");
//
//                } catch (final ApiException e) {
//                    logger.error("Failed to delete Pod Persistent Volume and/or Claim.", e);
//                }
//            }
//        });

            // delete StatefulSets
            k8sResourceUtils.listNamespacedStatefulSets(dataCenterKey.namespace, null, labelSelector).forEach(statefulSet -> {
                try (@SuppressWarnings("unused") final MDC.MDCCloseable _statefulSetMDC = putNamespacedName("StatefulSet", statefulSet.getMetadata())) {
                    try {
                        k8sResourceUtils.deleteStatefulSet(statefulSet);
                        logger.debug("Deleted StatefulSet.");

                    } catch (final JsonSyntaxException e) {
                        logger.debug("Caught JSON exception while deleting StatefulSet. Ignoring due to https://github.com/kubernetes-client/java/issues/86.", e);

                    } catch (final ApiException e) {
                        logger.error("Failed to delete StatefulSet.", e);
                    }
                }
            });

            // delete ConfigMaps
            k8sResourceUtils.listNamespacedConfigMaps(dataCenterKey.namespace, null, labelSelector).forEach(configMap -> {
                try (@SuppressWarnings("unused") final MDC.MDCCloseable _configMapMDC = putNamespacedName("ConfigMap", configMap.getMetadata())) {
                    try {
                        k8sResourceUtils.deleteConfigMap(configMap);
                        logger.debug("Deleted ConfigMap.");

                    } catch (final JsonSyntaxException e) {
                        logger.debug("Caught JSON exception while deleting ConfigMap. Iignoring due to https://github.com/kubernetes-client/java/issues/86.", e);

                    } catch (final ApiException e) {
                        logger.error("Failed to delete ConfigMap.", e);
                    }
                }
            });

            // delete Services
            k8sResourceUtils.listNamespacedServices(dataCenterKey.namespace, null, labelSelector).forEach(service -> {
                try (@SuppressWarnings("unused") final MDC.MDCCloseable _serviceMDC = putNamespacedName("Service", service.getMetadata())) {
                    try {
                        k8sResourceUtils.deleteService(service);
                        logger.debug("Deleted Service.");

                    } catch (final JsonSyntaxException e) {
                        logger.debug("Caught JSON exception while deleting Service. Ignoring due to https://github.com/kubernetes-client/java/issues/86.", e);

                    } catch (final ApiException e) {
                        logger.error("Failed to delete Service.", e);
                    }
                }
            });

            logger.info("Deleted DataCenter.");
        }
    }
}
