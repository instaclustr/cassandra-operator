package com.instaclustr.k8s.watch;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public class WatchService<ResourceT, ResourceListT, ResourceKeyT extends Key<ResourceT>> extends AbstractExecutionThreadService {
    private final Logger logger = LoggerFactory.getLogger(WatchService.class);

    private final TypeToken<ResourceT> resourceType = new TypeToken<ResourceT>(getClass()) {};
    private final TypeToken<ResourceListT> resourceListType = new TypeToken<ResourceListT>(getClass()) {};

    private final ApiClient apiClient;
    private final EventBus eventBus;

    private final ListCallProvider listCallProvider;
    private final WatchEvent.Factory<ResourceT> eventFactory;
    private final Map<ResourceKeyT, ResourceT> resourceCache;
    private final Function<ResourceT, ResourceKeyT> keyFunction;
    private final Function<ResourceT, V1ObjectMeta> resourceMetadataFunction;
    private final Function<ResourceListT, V1ListMeta> resourceListMetadataFunction;
    private final Function<ResourceListT, List<ResourceT>> resourceListItemsFunction;
    private final Gson gson;

    private Call currentCall;

    private enum ResponseType {
        ADDED,
        MODIFIED,
        DELETED,
        ERROR
    }

    public WatchService(final ApiClient apiClient, final EventBus eventBus,
                        final ListCallProvider listCallProvider,
                        final WatchEvent.Factory<ResourceT> eventFactory,
                        final Map<ResourceKeyT, ResourceT> resourceCache,
                        final Function<ResourceT, ResourceKeyT> keyFunction,
                        final Function<ResourceT, V1ObjectMeta> resourceMetadataFunction,
                        final Function<ResourceListT, V1ListMeta> resourceListMetadataFunction,
                        final Function<ResourceListT, List<ResourceT>> resourceListItemsFunction) {
        this.apiClient = apiClient;
        this.eventBus = eventBus;
        this.listCallProvider = listCallProvider;
        this.eventFactory = eventFactory;
        this.resourceCache = resourceCache;
        this.keyFunction = keyFunction;
        this.resourceMetadataFunction = resourceMetadataFunction;
        this.resourceListMetadataFunction = resourceListMetadataFunction;
        this.resourceListItemsFunction = resourceListItemsFunction;

        this.gson = apiClient.getJSON().getGson();


        apiClient.getHttpClient().setReadTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    protected String serviceName() {
        return WatchService.class.getSimpleName() + "(" + resourceType.getRawType().getSimpleName() + ")";
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

        currentCall = listCallProvider.get(listResourceVersion, true);

        try (final Watch<JsonObject> watch = Watch.createWatch(apiClient, currentCall, new TypeToken<Watch.Response<JsonObject>>() {}.getType())) {

            for (final Watch.Response<JsonObject> objectResponse : watch) {
                final ResponseType responseType = ResponseType.valueOf(objectResponse.type);

                if (responseType == ResponseType.ERROR) {
                    final V1Status status = gson.fromJson(objectResponse.object, new TypeToken<Watch.Response<V1Status>>() {}.getType());

                    logger.error("Resource list watch failed with {}.", status);

                    return; // force re-sync
                }

                final ResourceT resource = gson.fromJson(objectResponse.object, resourceType.getType());
                final ResourceKeyT key = keyFunction.apply(resource);

                switch (responseType) {
                    case ADDED:
                        addCachedResource(key, resource);
                        break;

                    case MODIFIED:
                        updateCachedResource(key, resource);
                        break;

                    case DELETED:
                        removeCachedResource(key);
                        break;
                }
            }

        } catch (final RuntimeException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof java.net.SocketTimeoutException) {
                return; // restart the watch by forcing a re-sync
            }

            throw e;
        }
    }

    private String syncResourceCache() throws ApiException {
        logger.debug("Synchronising local cache.");

        final ResourceListT apiResponseData;
        {
            final ApiResponse<ResourceListT> apiResponse = apiClient.execute(listCallProvider.get(null, false), resourceListType.getType());

            // TODO: is it necessary to handle different response statuses here...

            apiResponseData = apiResponse.getData();
        }

        final Map<ResourceKeyT, ResourceT> remoteResources = resourceListItemsFunction.apply(apiResponseData).stream()
                .collect(ImmutableMap.toImmutableMap(keyFunction, Function.identity()));

        // remove non-existent resources from local cache
        // (immutable copy to prevent concurrent modification, as Sets::difference is a view over the underlying set/map)
        ImmutableSet.copyOf(Sets.difference(resourceCache.keySet(), remoteResources.keySet())).forEach(this::removeCachedResource);

        // update resources in local cache
        Sets.intersection(resourceCache.keySet(), remoteResources.keySet()).forEach(key -> {
            updateCachedResource(key, remoteResources.get(key));
        });

        // add any new resources to local cache
        Sets.difference(remoteResources.keySet(), resourceCache.keySet()).forEach(key -> {
            addCachedResource(key, remoteResources.get(key));
        });

        return resourceListMetadataFunction.apply(apiResponseData).getResourceVersion();
    }

    private void addCachedResource(final ResourceKeyT key, final ResourceT resource) {
        logger.trace("Adding resource to local cache. Will post added event.");

        resourceCache.put(key, resource);
        eventBus.post(eventFactory.createAddedEvent(resource));
    }

    private void updateCachedResource(final ResourceKeyT key, final ResourceT newResource) {
        logger.trace("Updating locally cached resource.");

        final ResourceT oldResource = resourceCache.put(key, newResource);

        final V1ObjectMeta oldMetadata = resourceMetadataFunction.apply(oldResource);
        final V1ObjectMeta newMetadata = resourceMetadataFunction.apply(newResource);

        if (!oldMetadata.getResourceVersion().equals(newMetadata.getResourceVersion())) {
            logger.trace("Remote and locally cached resource versions differ. Posting modified event.");
            eventBus.post(eventFactory.createModifiedEvent(oldResource, newResource));
        }
    }

    private void removeCachedResource(final ResourceKeyT key) {
        logger.trace("Removing resource from local cache. Will post deleted event.", key);

        final ResourceT resource = resourceCache.remove(key);
        eventBus.post(eventFactory.createDeletedEvent(resource));
    }

    @Override
    protected void triggerShutdown() {
        if (currentCall != null) {
            currentCall.cancel();
        }
    }
}
