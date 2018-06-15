package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import io.kubernetes.client.models.V1beta2StatefulSet;

import javax.annotation.Nullable;
import javax.inject.Inject;

@SuppressWarnings("WeakerAccess")
public abstract class StatefulSetWatchEvent extends WatchEvent {
    public final V1beta2StatefulSet statefulSet;

    protected StatefulSetWatchEvent(final V1beta2StatefulSet statefulSet) {
        this.statefulSet = statefulSet;
    }

    public interface Factory extends WatchEvent.Factory<V1beta2StatefulSet> {
        Added createAddedEvent(final V1beta2StatefulSet statefulSet);
        Modified createModifiedEvent(@Nullable @Assisted("old")  final V1beta2StatefulSet oldStatefulSet, @Assisted("new") final V1beta2StatefulSet newStatefulSet);
        Deleted createDeletedEvent(final V1beta2StatefulSet statefulSet);
    }

    public static class Added extends StatefulSetWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final V1beta2StatefulSet secret) {
            super(secret);
        }
    }

    public static class Modified extends StatefulSetWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final V1beta2StatefulSet oldStatefulSet;

        @Inject
        public Modified(@Nullable @Assisted("old") final V1beta2StatefulSet oldStatefulSet, @Assisted("new") final V1beta2StatefulSet newStatefulSet) {
            super(newStatefulSet);
            this.oldStatefulSet = oldStatefulSet;
        }
    }

    public static class Deleted extends StatefulSetWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final V1beta2StatefulSet secret) {
            super(secret);
        }
    }
}
