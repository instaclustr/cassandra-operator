package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import io.kubernetes.client.models.V1ConfigMap;

import javax.annotation.Nullable;
import javax.inject.Inject;

@SuppressWarnings("WeakerAccess")
public abstract class ConfigMapWatchEvent extends WatchEvent {
    public final V1ConfigMap configMap;

    protected ConfigMapWatchEvent(final V1ConfigMap configMap) {
        this.configMap = configMap;
    }

    public interface Factory extends WatchEvent.Factory<V1ConfigMap> {
        Added createAddedEvent(final V1ConfigMap configMap);
        Modified createModifiedEvent(@Nullable @Assisted("old") final V1ConfigMap oldConfigMap, @Assisted("new") final V1ConfigMap newConfigMap);
        Deleted createDeletedEvent(final V1ConfigMap configMap);
    }

    public static class Added extends ConfigMapWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final V1ConfigMap configMap) {
            super(configMap);
        }
    }

    public static class Modified extends ConfigMapWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final V1ConfigMap oldConfigMap;

        @Inject
        public Modified(@Nullable @Assisted("old") final V1ConfigMap oldConfigMap, @Assisted("new") final V1ConfigMap newConfigMap) {
            super(newConfigMap);
            this.oldConfigMap = oldConfigMap;
        }
    }

    public static class Deleted extends ConfigMapWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final V1ConfigMap configMap) {
            super(configMap);
        }
    }
}
