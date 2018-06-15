package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.model.Cluster;

import javax.annotation.Nullable;
import javax.inject.Inject;

@SuppressWarnings("WeakerAccess")
public abstract class ClusterWatchEvent extends WatchEvent {
    public final Cluster cluster;

    protected ClusterWatchEvent(final Cluster cluster) {
        this.cluster = cluster;
    }

    public interface Factory extends WatchEvent.Factory<Cluster> {
        Added createAddedEvent(final Cluster cluster);
        Modified createModifiedEvent(@Nullable @Assisted("old") final Cluster oldCluster, @Assisted("new") final Cluster newCluster);
        Deleted createDeletedEvent(final Cluster cluster);
    }

    public static class Added extends ClusterWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final Cluster cluster) {
            super(cluster);
        }
    }

    public static class Modified extends ClusterWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final Cluster oldCluster;

        @Inject
        public Modified(@Nullable @Assisted("old") final Cluster oldCluster, @Assisted("new") final Cluster newCluster) {
            super(newCluster);
            this.oldCluster = oldCluster;
        }
    }

    public static class Deleted extends ClusterWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final Cluster cluster) {
            super(cluster);
        }
    }
}
