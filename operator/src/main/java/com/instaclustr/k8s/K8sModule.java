package com.instaclustr.k8s;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.auth.ApiKeyAuth;
import io.kubernetes.client.util.Config;

import java.io.IOException;

public class K8sModule extends AbstractModule {
    static final String externalK8sConfig = System.getProperty("com.instaclustr.k8s.operator.externalk8s");

    @Provides
    public ApiClient provideApiClient() {
        ApiClient apiClient;
        try {
            if(externalK8sConfig != null) {
                //TODO: make this externally configurable
                apiClient = Config.fromConfig(externalK8sConfig);
            } else {
                apiClient = Config.defaultClient();
            }
        } catch (IOException e) {
            //Just throw this and die
            throw new RuntimeException(e);
        }

        return apiClient;
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

}
