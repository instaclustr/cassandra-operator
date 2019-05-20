package com.instaclustr.cassandra.operator.k8s;

import com.google.common.base.Strings;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta2Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;

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
                    () -> {
                        // temporarily disable service replace call to fix issue #41 since service can't be customized right now
//                        coreApi.replaceNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), service, null, null);
//                        logger.debug("Replaced namespaced Service.");
                    }
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

        coreApi.deleteNamespacedService(metadata.getName(), metadata.getNamespace(), new V1DeleteOptions(), null, null, null, null, null);
    }

    public void deleteConfigMap(final V1ConfigMap configMap) throws ApiException {
        final V1ObjectMeta configMapMetadata = configMap.getMetadata();

        coreApi.deleteNamespacedConfigMap(configMapMetadata.getName(), configMapMetadata.getNamespace(), new V1DeleteOptions(), null, null, null, null, null);
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

    public void deletePersistentPersistentVolumeClaims(final String labels, final String namespace) throws ApiException {
            logger.debug("Deleting Pod Persistent Volumes and Claims.");
            coreApi.deleteCollectionNamespacedPersistentVolumeClaim(namespace, true, null, null, null, labels, null, null, null, null);
    }



    static class ResourceListIterable<T> implements Iterable<T> {
        interface Page<T> {
            Collection<T> items();

            Page<T> nextPage() throws ApiException;
        }

        private Page<T> firstPage;

        ResourceListIterable(final Page<T> firstPage) {
            this.firstPage = firstPage;
        }

        @Override
        public Iterator<T> iterator() {
            return Iterators.concat(new AbstractIterator<Iterator<T>>() {
                Page<T> currentPage = firstPage;

                @Override
                protected Iterator<T> computeNext() {
                    if (currentPage == null)
                        return endOfData();

                    final Iterator<T> iterator = currentPage.items().iterator();

                    try {
                        currentPage = currentPage.nextPage();

                    } catch (final ApiException e) {
                        throw new RuntimeException(e);
                    }

                    return iterator;
                }
            });
        }
    }

    public Iterable<V1Pod> listNamespacedPods(final String namespace, @Nullable final String fieldSelector, @Nullable final String labelSelector) throws ApiException {
        class V1PodPage implements ResourceListIterable.Page<V1Pod> {
            private final V1PodList podList;

            private V1PodPage(final String continueToken) throws ApiException {
                podList = coreApi.listNamespacedPod(namespace, null, null, continueToken, fieldSelector, labelSelector, null, null, null, null);
            }

            @Override
            public Collection<V1Pod> items() {
                return podList.getItems();
            }

            @Override
            public V1PodPage nextPage() throws ApiException {
                final String continueToken = podList.getMetadata().getContinue();

                if (Strings.isNullOrEmpty(continueToken))
                    return null;

                return new V1PodPage(continueToken);
            }
        }

        final V1PodPage firstPage = new V1PodPage(null);

        return new ResourceListIterable<>(firstPage);
    }

    public Iterable<V1beta2StatefulSet> listNamespacedStatefulSets(final String namespace, @Nullable final String fieldSelector, @Nullable final String labelSelector) throws ApiException {
        class V1beta2StatefulSetPage implements ResourceListIterable.Page<V1beta2StatefulSet> {
            private final V1beta2StatefulSetList statefulSetList;

            private V1beta2StatefulSetPage(final String continueToken) throws ApiException {
                statefulSetList = appsApi.listNamespacedStatefulSet(namespace, null, null, continueToken, fieldSelector, labelSelector, null, null, null, null);
            }

            @Override
            public Collection<V1beta2StatefulSet> items() {
                return statefulSetList.getItems();
            }

            @Override
            public ResourceListIterable.Page<V1beta2StatefulSet> nextPage() throws ApiException {
                final String continueToken = statefulSetList.getMetadata().getContinue();

                if (Strings.isNullOrEmpty(continueToken))
                    return null;

                return new V1beta2StatefulSetPage(continueToken);
            }
        }

        final V1beta2StatefulSetPage firstPage = new V1beta2StatefulSetPage(null);

        return new ResourceListIterable<>(firstPage);
    }


    public Iterable<V1ConfigMap> listNamespacedConfigMaps(final String namespace, @Nullable final String fieldSelector, @Nullable final String labelSelector) throws ApiException {
        class V1ConfigMapPage implements ResourceListIterable.Page<V1ConfigMap> {
            private final V1ConfigMapList configMapList;

            private V1ConfigMapPage(final String continueToken) throws ApiException {
                configMapList = coreApi.listNamespacedConfigMap(namespace, null, null, continueToken, fieldSelector, labelSelector, null, null, null, null);
            }

            @Override
            public Collection<V1ConfigMap> items() {
                return configMapList.getItems();
            }

            @Override
            public ResourceListIterable.Page<V1ConfigMap> nextPage() throws ApiException {
                final String continueToken = configMapList.getMetadata().getContinue();

                if (Strings.isNullOrEmpty(continueToken))
                    return null;

                return new V1ConfigMapPage(continueToken);
            }
        }

        final V1ConfigMapPage firstPage = new V1ConfigMapPage(null);

        return new ResourceListIterable<>(firstPage);
    }

    public Iterable<V1Service> listNamespacedServices(final String namespace, @Nullable final String fieldSelector, @Nullable final String labelSelector) throws ApiException {
        class V1ServicePage implements ResourceListIterable.Page<V1Service> {
            private final V1ServiceList serviceList;

            private V1ServicePage(final String continueToken) throws ApiException {
                serviceList = coreApi.listNamespacedService(namespace, null, null, continueToken, fieldSelector, labelSelector, null, null, null, null);
            }

            @Override
            public Collection<V1Service> items() {
                return serviceList.getItems();
            }

            @Override
            public ResourceListIterable.Page<V1Service> nextPage() throws ApiException {
                final String continueToken = serviceList.getMetadata().getContinue();

                if (Strings.isNullOrEmpty(continueToken))
                    return null;

                return new V1ServicePage(continueToken);
            }
        }

        final V1ServicePage firstPage = new V1ServicePage(null);

        return new ResourceListIterable<>(firstPage);
    }
}
