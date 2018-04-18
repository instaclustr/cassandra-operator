package com.instaclustr.k8s;

import com.google.common.cache.Cache;
import com.google.common.eventbus.EventBus;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.event.DataCenterEvent;
import com.instaclustr.cassandra.operator.event.WatchEvent;
import com.instaclustr.cassandra.operator.model.Cluster;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;

import javax.inject.Inject;
import java.lang.reflect.Type;

public class WatchService extends AbstractExecutionThreadService {

    private final ApiClient apiClient;
    private final CustomObjectsApi customObjectsApi;
    private final EventBus eventBus;
    private final WatchEvent.Factory<DataCenter> dataCenterEventFactory;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;

    @Inject
    public WatchService(final ApiClient apiClient, final CustomObjectsApi customObjectsApi, final EventBus eventBus, final DataCenterEvent.Factory dataCenterEventFactory, final Cache<DataCenterKey, DataCenter> dataCenterCache) {
        this.apiClient = apiClient;
        this.customObjectsApi = customObjectsApi;
        this.eventBus = eventBus;
        this.dataCenterEventFactory = dataCenterEventFactory;
        this.dataCenterCache = dataCenterCache;
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


    @Override
    protected void run() throws Exception {
        String resourceVersion = null;

        while (isRunning()) {
            try {
                final Watch<DataCenter> dataCentersWatch = Watch.createWatch(apiClient,
                        customObjectsApi.listClusterCustomObjectCall("stable.instaclustr.com", "v1", "datacentres", null, null, resourceVersion, true, null, null),
                        new TypeToken<Watch.Response<DataCenter>>() {}.getType()
                );
//
                for (final Watch.Response<DataCenter> objectResponse : dataCentersWatch) {
                    final DataCenter object = objectResponse.object;

                    resourceVersion = object.getMetadata().getResourceVersion();

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
