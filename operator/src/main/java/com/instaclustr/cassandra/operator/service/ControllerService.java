package com.instaclustr.cassandra.operator.service;

import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.model.Cluster;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;

import javax.inject.Inject;

public class ControllerService extends AbstractExecutionThreadService {

    private final ApiClient apiClient;
    private final CustomObjectsApi customObjectsApi;

    @Inject
    public ControllerService(final ApiClient apiClient, final CustomObjectsApi customObjectsApi) {
        this.apiClient = apiClient;
        this.customObjectsApi = customObjectsApi;
    }

    @Override
    protected void run() throws Exception {
        String resourceVersion = null;

        while (isRunning()) {
            try {
                final Watch<Cluster> watch = Watch.createWatch(apiClient,
                        customObjectsApi.listClusterCustomObjectCall("stable.instaclustr.com", "v1", "clusters", null, null, resourceVersion, true, null, null),
                        new TypeToken<Watch.Response<Cluster>>() {}.getType()
                );

                for (final Watch.Response<Cluster> objectResponse : watch) {
                    resourceVersion = objectResponse.object.getMetadata().getResourceVersion();

                    System.out.printf("%s %s%n", objectResponse.object, objectResponse.type);
                }

            } catch (final RuntimeException e) {
                final Throwable cause = e.getCause();

                if (cause instanceof java.net.SocketTimeoutException)
                    continue;

                throw e; // TODO: maybe unwrap/throw `cause` -- but its a Throwable
            }
        }
    }
}
