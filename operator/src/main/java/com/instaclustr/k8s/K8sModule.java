package com.instaclustr.k8s;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.*;
import io.kubernetes.client.util.ClientBuilder;

public class K8sModule extends AbstractModule {

    @Provides
    public ApiClient provideApiClient(final ClientBuilder clientBuilder) {
        return clientBuilder.build();
    }

    @Provides
    public CoreV1Api provideCoreV1Api(final ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Provides
    public ApiextensionsV1beta1Api providesApiExtensionsV1beta1Api(final ApiClient apiClient) {
        return new ApiextensionsV1beta1Api(apiClient);
    }

    @Provides
    public CustomObjectsApi provideCustomObjectsApi(final ApiClient apiClient) {
        return new CustomObjectsApi(apiClient);
    }

    @Provides
    public VersionApi provideVersionApi(final ApiClient apiClient) {
        return new VersionApi(apiClient);
    }

    @Provides
    public AppsV1beta2Api provideAppsV1beta2Api(final ApiClient apiClient) {
        return new AppsV1beta2Api(apiClient);
    }

}
