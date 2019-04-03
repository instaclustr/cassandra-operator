package com.instaclustr.k8s.watch;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.event.WatchEvent;
import com.instaclustr.cassandra.operator.model.key.Key;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ApiResponse;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public abstract class WatchService<ResourceT, ResourceListT, ResourceKeyT extends Key<ResourceT>> extends AbstractExecutionThreadService {
    private final Logger logger = LoggerFactory.getLogger(WatchService.class);

    private final TypeToken<ResourceT> resourceType = new TypeToken<ResourceT>(getClass()) {};
    private final TypeToken<ResourceListT> resourceListType = new TypeToken<ResourceListT>(getClass()) {};

    private final ApiClient apiClient;

    private final ResourceCache<ResourceKeyT, ResourceT> resourceCache;
    private final Gson gson;

    private Call currentCall;

    private enum ResponseType {
        ADDED,
        MODIFIED,
        DELETED,
        ERROR
    }

    public WatchService(final ApiClient apiClient,
                        final ResourceCache<ResourceKeyT, ResourceT> resourceCache) {
        this.apiClient = apiClient;
        this.resourceCache = resourceCache;

        this.gson = apiClient.getJSON().getGson();

        apiClient.getHttpClient().setReadTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    protected String serviceName() {
        return WatchService.class.getSimpleName() + " (" + resourceType.getRawType().getSimpleName() + ")";
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            final String listResourceVersion = syncResourceCache();

            watchResourceList(listResourceVersion);
        }
    }

    private void watchResourceList(final String listResourceVersion) throws ApiException, IOException {
        logger.debug("Watching resource list.");

        currentCall = listResources(null, listResourceVersion, true);

        try (final Watch<JsonObject> watch = Watch.createWatch(apiClient, currentCall, new TypeToken<Watch.Response<JsonObject>>() {}.getType())) {
            for (final Watch.Response<JsonObject> objectResponse : watch) {
                final ResponseType responseType = ResponseType.valueOf(objectResponse.type);

                if (responseType == ResponseType.ERROR) {
                    final V1Status status = gson.fromJson(objectResponse.object, new TypeToken<Watch.Response<V1Status>>() {}.getType());

                    logger.error("Resource list watch failed with {}.", status);

                    return; // force re-sync
                }

                final ResourceT resource = gson.fromJson(objectResponse.object, resourceType.getType());

                switch (responseType) {
                    case ADDED:
                    case MODIFIED:
                        resourceCache.put(resource);
                        break;

                    case DELETED:
                        resourceCache.remove(resource);
                        break;
                }
            }

        } catch (final RuntimeException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof java.net.SocketTimeoutException) {
                logger.debug("Resource list watch expired. Re-syncing.");
                return; // restart the watch by forcing a re-sync
            }

            throw e;
        }
    }

    protected abstract Call listResources(final String continueToken, final String resourceVersion, final boolean watch) throws ApiException;

    private String syncResourceCache() throws ApiException {
        logger.debug("Synchronising local cache.");

        final List<ResourceT> resources = new ArrayList<>();

        V1ListMeta lastMetadata = null;
        String continueToken = null;
        do {
            final ApiResponse<ResourceListT> apiResponse = apiClient.execute(listResources(continueToken, null, false), resourceListType.getType());

            // TODO: is it necessary to handle different response statuses here...

            final ResourceListT apiResponseData = apiResponse.getData();

            lastMetadata = resourceListMetadata(apiResponseData);

            resources.addAll(resourceListItems(apiResponseData));

            continueToken = lastMetadata.getContinue();
        } while (!Strings.isNullOrEmpty(continueToken));

        resourceCache.sync(resources);

        logger.debug("Local cache synchronised.");

        return lastMetadata.getResourceVersion();
    }

    protected abstract Collection<? extends ResourceT> resourceListItems(final ResourceListT resourceList);

    protected abstract V1ListMeta resourceListMetadata(final ResourceListT resourceList);

    @Override
    protected void triggerShutdown() {
        if (currentCall != null) {
            currentCall.cancel();
        }
    }
}
