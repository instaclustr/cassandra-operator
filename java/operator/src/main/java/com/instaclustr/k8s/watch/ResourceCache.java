package com.instaclustr.k8s.watch;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.reflect.TypeToken;
import com.instaclustr.cassandra.operator.event.WatchEvent;
import com.instaclustr.cassandra.operator.model.key.Key;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.models.V1ObjectMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

public abstract class ResourceCache<ResourceKeyT extends Key<ResourceT>, ResourceT> {
    private final Logger logger = LoggerFactory.getLogger(ResourceCache.class);

    private final EventBus eventBus;
    private final WatchEvent.Factory<ResourceT> eventFactory;

    private final Map<ResourceKeyT, ResourceT> cache = new HashMap<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final TypeToken<ResourceT> resourceType = new TypeToken<ResourceT>(getClass()) {};

    public ResourceCache(final EventBus eventBus,
                         final WatchEvent.Factory<ResourceT> eventFactory) {
        this.eventBus = eventBus;
        this.eventFactory = eventFactory;
    }

    public ResourceT get(final ResourceKeyT key) throws InterruptedException {
        // block until the cache has been initialized by the initial sync
        latch.await();

        return cache.get(key);
    }

    void sync(final List<ResourceT> resourceList) {
        final Map<ResourceKeyT, ResourceT> remoteResources = resourceList.stream()
                .collect(ImmutableMap.toImmutableMap(this::resourceKey, Function.identity()));

        // remove non-existent resources from local cache
        // (immutable copy to prevent concurrent modification, as Sets::difference is a view over the underlying set/map)
        ImmutableSet.copyOf(Sets.difference(cache.keySet(), remoteResources.keySet()))
                .forEach(this::remove);

        // update resources in local cache
        remoteResources.forEach(this::put);

        latch.countDown();
    }

    void put(final ResourceT resource) {
        final ResourceKeyT key = resourceKey(resource);

        put(key, resource);
    }

    private MDC.MDCCloseable putResourceKeyInMDC(final ResourceKeyT key) {
        return MDC.put("ResourceKey", key.toString()).andPut("ResourceType", resourceType.toString());
    }


    private void put(final ResourceKeyT key, final ResourceT resource) {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _resourceKeyMDC = putResourceKeyInMDC(key)) {
            final ResourceT oldResource = cache.put(key, resource);

            if (oldResource == null) {
                // new resource (previously not in cache)
                logger.debug("Added resource to local cache. Posting added event.", key);
                eventBus.post(eventFactory.createAddedEvent(resource));
                return;
            }

            final String oldResourceVersion = resourceMetadata(oldResource).getResourceVersion();
            final String newResourceVersion = resourceMetadata(resource).getResourceVersion();

            if (!oldResourceVersion.equals(newResourceVersion)) {
                logger.debug("Remote and locally cached resource versions differ. Posting modified event. old = {}, new = {}.", oldResourceVersion, newResourceVersion);
                eventBus.post(eventFactory.createModifiedEvent(oldResource, resource));
            }
        }
    }


    void remove(final ResourceT resource) {
        final ResourceKeyT key = resourceKey(resource);

        try (@SuppressWarnings("unused") final MDC.MDCCloseable _resourceKeyMDC = putResourceKeyInMDC(key)) {
            logger.debug("Removing resource from local cache. Will post deleted event.");

            // ignore the local version -- the server sends a copy of the last version of the resource, which may be newer than the cached copy
            cache.remove(key);
            eventBus.post(eventFactory.createDeletedEvent(resource));
        }
    }

    private void remove(final ResourceKeyT key) {
        try (@SuppressWarnings("unused") final MDC.MDCCloseable _resourceKeyMDC = putResourceKeyInMDC(key)) {
            logger.debug("Removing resource from local cache. Will post deleted event.");

            final ResourceT resource = cache.remove(key);
            eventBus.post(eventFactory.createDeletedEvent(resource));
        }
    }


    protected abstract ResourceKeyT resourceKey(final ResourceT resource);

    protected abstract V1ObjectMeta resourceMetadata(final ResourceT resource);

    public Set<Map.Entry<ResourceKeyT, ResourceT>> entrySet() {
        return cache.entrySet();
    }
}
