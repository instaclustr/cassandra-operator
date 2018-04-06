package com.instaclustr.k8s;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.auth.ApiKeyAuth;

public class K8sModule extends AbstractModule {

    static final String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tbjdrZjgiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6ImQ0MGQ2ZTUxLTM0NWMtMTFlOC04M2MzLTA4MDAyNzFhMWNkMCIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.hyodcibMXdvMvw4s9xALvGArIKi9oT06q435om-EeBYcTvMf9a4N5SmnAoR14AKh1Ge0FxA3OvLquIzy0OF6Dc2H4uIakzXTddNN0O2LBAyvOmeQGDWjc6MXBK-c5rOIOU0_ZZU-RT2B-l0mbLaUSPjKzVZV0h27lTiaC8QMEX8Lpn_SBxLAM06bqWzUBw7qvhYqCVhWr9ZEryURFYce9e7lmn1pyDpyX7Ttyoc68csHWv6ZXx5Tj3P047HOfl5E80MnMJwIhYvvumTEtQzrn80tOtOZQrAaZ9wN7Z4K3UO1TNUAHrdHHMmshPksTkck4S55pfmHORx4uAF_QoDYzA";

    @Provides
    public ApiClient provideApiClient() {
        final ApiClient apiClient = new ApiClient();

        apiClient.setBasePath("https://192.168.99.100:8443");
        apiClient.setVerifyingSsl(false);

        final ApiKeyAuth bearerToken = (ApiKeyAuth) apiClient.getAuthentication("BearerToken");

        bearerToken.setApiKey(token);
        bearerToken.setApiKeyPrefix("Bearer");

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
