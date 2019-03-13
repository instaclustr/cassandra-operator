package com.instaclustr.k8s;

import com.google.inject.AbstractModule;
//import com.google.inject.Provides;
import com.google.inject.Provides;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.*;
import io.kubernetes.client.util.ClientBuilder;

import javax.inject.Singleton;

public class K8sModule extends AbstractModule {
    @Provides
    @Singleton
    public CoreV1Api provideCoreV1Api(final ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Provides
    @Singleton
    public ApiextensionsV1beta1Api providesApiExtensionsV1beta1Api(final ApiClient apiClient) {
        return new ApiextensionsV1beta1Api(apiClient);
    }

    @Provides
    @Singleton
    public CustomObjectsApi provideCustomObjectsApi(final ApiClient apiClient) {
        return new CustomObjectsApi(apiClient);
    }

    @Provides
    @Singleton
    public VersionApi provideVersionApi(final ApiClient apiClient) {
        return new VersionApi(apiClient);
    }

    @Provides
    @Singleton
    public AppsV1beta2Api provideAppsV1beta2Api(final ApiClient apiClient) {
        return new AppsV1beta2Api(apiClient);
    }
}
