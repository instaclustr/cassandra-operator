package com.instaclustr.cassandra.operator.service;

import com.google.common.cache.Cache;
import com.google.common.eventbus.EventBus;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.event.DataCenterWatchEvent;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;


// TODO: merge this with WatchService (once complete)
public class DataCenterWatchService extends AbstractExecutionThreadService {
    static final Logger logger = LoggerFactory.getLogger(DataCenterWatchService.class);

    private final ApiClient apiClient;
    private final CustomObjectsApi customObjectsApi;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;
    private final DataCenterWatchEvent.Factory dataCenterEventFactory;
    private final EventBus eventBus;

    @Inject
    DataCenterWatchService(final ApiClient apiClient,
            final CustomObjectsApi customObjectsApi,
            final Cache<DataCenterKey, DataCenter> dataCenterCache,
            final DataCenterWatchEvent.Factory dataCenterEventFactory,
            final EventBus eventBus) {
        this.apiClient = apiClient;
        this.customObjectsApi = customObjectsApi;
        this.dataCenterCache = dataCenterCache;
        this.dataCenterEventFactory = dataCenterEventFactory;
        this.eventBus = eventBus;

    }


    private enum ResponseType {
        ADDED,
        MODIFIED,
        DELETED,
        ERROR
    }

    // TODO: make the watches configurable via injection of something like the WatchConfig object below.

    //    static class WatchConfig<K, T> {
    //        public final Call watchCall;
    //        public final Type responseType = new TypeToken<Watch.Response<T>>() {}.getType();
    //        final WatchEvent.Factory<T> watchEventFactory;
    //        final Cache<K, T> responseCache;
    //
    //
    //    }


    protected void run() throws Exception {
        String resourceVersion = null;

        while (isRunning()) {
            try {
                final Watch<DataCenter> dataCentersWatch = Watch.createWatch(apiClient,
                        customObjectsApi.listClusterCustomObjectCall("stable.instaclustr.com", "v1", "cassandra-datacenters", null, null, resourceVersion, true, null, null),
                        new TypeToken<Watch.Response<DataCenter>>() {}.getType()
                        );
                //
                for (final Watch.Response<DataCenter> objectResponse : dataCentersWatch) {
                    final DataCenter object = objectResponse.object;

                    resourceVersion = object.getMetadata().getResourceVersion();

                    logger.debug("Watch over datacenters received {}", objectResponse.type);

                    switch (ResponseType.valueOf(objectResponse.type)) {
                    case ADDED:
                        dataCenterCache.put(DataCenterKey.forDataCenter(object), object);
                        eventBus.post(dataCenterEventFactory.createAddedEvent(object));
                        break;

                    case MODIFIED:
                        dataCenterCache.put(DataCenterKey.forDataCenter(object), object);
                        eventBus.post(dataCenterEventFactory.createModifiedEvent(object, object));
                        break;

                    case DELETED:
                        dataCenterCache.invalidate(DataCenterKey.forDataCenter(object));
                        eventBus.post(dataCenterEventFactory.createDeletedEvent(object));
                        break;

                    case ERROR:
                        // TODO: handle error? -- maybe?
                        break;
                    }

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
