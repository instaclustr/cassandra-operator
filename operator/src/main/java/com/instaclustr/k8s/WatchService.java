//package com.instaclustr.k8s;
//
//import com.google.common.collect.ImmutableSet;
//import com.google.common.eventbus.EventBus;
//import com.google.common.util.concurrent.*;
//import io.kubernetes.client.ApiClient;
//
//import javax.inject.Inject;
//import java.util.Set;
//
//public class WatchService extends AbstractService {
//
//    private final ServiceManager watchServiceManager;
//
//    private final ApiClient apiClient;
//    private final EventBus eventBus;
//
//    @Inject
//    WatchService(final ApiClient apiClient,
//                        final Set<WatchConfig> watchConfigs,
//                        final EventBus eventBus) {
//        this.apiClient = apiClient;
////        this.watchConfigs = watchConfigs;
//        this.eventBus = eventBus;
//
//
//        watchServiceManager = new ServiceManager(watchConfigServices(watchConfigs));
//        watchServiceManager.addListener(new ServiceManager.Listener() {
//            @Override
//            public void healthy() {
//                WatchService.this.notifyStarted();
//            }
//
//            @Override
//            public void stopped() {
//                WatchService.this.notifyStopped();
//            }
//
//            @Override
//            public void failure(final Service service) {
//                WatchService.this.notifyFailed(service.failureCause());
//            }
//        });
//    }
//
//    static Set<Service> watchConfigServices(final Set<WatchConfig> watchConfigs) {
//        final ImmutableSet.Builder<Service> builder = ImmutableSet.<Service>builder();
//
//        for (final WatchConfig watchConfig : watchConfigs) {
//            builder.add(new WatchConfigService(watchConfig));
//        }
//
//
//        return builder.build();
//    }
//
//    @Override
//    protected void doStart() {
//        watchServiceManager.startAsync();
//    }
//
//    @Override
//    protected void doStop() {
//        watchServiceManager.stopAsync();
//    }
//
//
//    private enum ResponseType {
//        ADDED,
//        MODIFIED,
//        DELETED,
//        ERROR
//    }
//
//    private static class WatchConfigService extends AbstractService {
//        public WatchConfigService(final WatchConfig watchConfig) {
//
//        }
//
////        @Override
////        protected void run() throws Exception {
////            String resourceVersion = null;
////
////            while (isRunning()) {
////                try {
////
////                }
////            }
////        }
//
//        @Override
//        protected void doStart() {
//            notifyStarted();
//        }
//
//        @Override
//        protected void doStop() {
//            notifyStopped();
//        }
//    }
//
//    // TODO: make the watches configurable via injection of something like the WatchConfig object below.
//
////    static class WatchConfig<K, T> {
////        public final Call watchCall;
////        public final Type responseType = new TypeToken<Watch.Response<T>>() {}.getType();
////        final WatchEvent.Factory<T> watchEventFactory;
////        final Cache<K, T> responseCache;
////
////
////    }
//
//
////    protected void run() throws Exception {
////        String resourceVersion = null;
////
////        while (isRunning()) {
////            try {
////                final Watch<DataCenter> dataCentersWatch = Watch.createWatch(apiClient,
////                        customObjectsApi.listClusterCustomObjectCall("stable.instaclustr.com", "v1", "datacenters", null, null, resourceVersion, true, null, null),
////                        new TypeToken<Watch.Response<DataCenter>>() {}.getType()
////                );
//////
////                for (final Watch.Response<DataCenter> objectResponse : dataCentersWatch) {
////                    final DataCenter object = objectResponse.object;
////
////                    resourceVersion = object.getMetadata().getResourceVersion();
////
////                    switch (ResponseType.valueOf(objectResponse.type)) {
////                        case ADDED:
////                            dataCenterCache.put(DataCenterKey.forDataCenter(object), object);
////
////                            eventBus.post(dataCenterEventFactory.createAddedEvent(object));
////                            break;
////
////                        case MODIFIED:
////                            dataCenterCache.put(DataCenterKey.forDataCenter(object), object);
////                            eventBus.post(dataCenterEventFactory.createModifiedEvent(object, object));
////                            break;
////
////                        case DELETED:
////                            eventBus.post(dataCenterEventFactory.createDeletedEvent(object));
////                            break;
////
////                        case ERROR:
////                            // TODO: handle error? -- maybe?
////                            break;
////                    }
////
////                }
////
////            } catch (final RuntimeException e) {
////                final Throwable cause = e.getCause();
////
////                if (cause instanceof java.net.SocketTimeoutException)
////                    continue;
////
////                throw e; // TODO: maybe unwrap/throw `cause` -- but its a Throwable
////            }
////        }
////    }
//}
