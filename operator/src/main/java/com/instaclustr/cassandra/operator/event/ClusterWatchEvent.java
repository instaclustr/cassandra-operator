package com.instaclustr.cassandra.operator.event;

import com.instaclustr.cassandra.operator.model.Cluster;

public abstract class ClusterWatchEvent {
    public final Cluster cluster;

    protected ClusterWatchEvent(final Cluster cluster) {
        this.cluster = cluster;
    }

    public interface Factory extends WatchEvent.Factory<Cluster> {
        Added createAddedEvent(final Cluster cluster);
        Modified createModifiedEvent(final Cluster cluster);
        Deleted createDeletedEvent(final Cluster cluster);
    }

    public static class Added extends ClusterWatchEvent implements WatchEvent.Added {
        public Added(final Cluster cluster) {
            super(cluster);
        }
    }

    public static class Modified extends ClusterWatchEvent implements WatchEvent.Modified {
        public Modified(final Cluster cluster) {
            super(cluster);
        }
    }

    public static class Deleted extends ClusterWatchEvent implements WatchEvent.Deleted {
        public Deleted(final Cluster cluster) {
            super(cluster);
        }
    }
}
