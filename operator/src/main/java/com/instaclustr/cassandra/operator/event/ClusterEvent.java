package com.instaclustr.cassandra.operator.event;

import com.instaclustr.cassandra.operator.model.Cluster;

public abstract class ClusterEvent {
    public final Cluster cluster;

    protected ClusterEvent(final Cluster cluster) {
        this.cluster = cluster;
    }

    public interface Factory extends WatchEvent.Factory<Cluster> {
        Added createAddedEvent(final Cluster cluster);
        Modified createModifiedEvent(final Cluster cluster);
        Deleted createDeletedEvent(final Cluster cluster);
    }

    public static class Added extends ClusterEvent implements WatchEvent.Added {
        public Added(final Cluster cluster) {
            super(cluster);
        }
    }

    public static class Modified extends ClusterEvent implements WatchEvent.Modified {
        public Modified(final Cluster cluster) {
            super(cluster);
        }
    }

    public static class Deleted extends ClusterEvent implements WatchEvent.Deleted {
        public Deleted(final Cluster cluster) {
            super(cluster);
        }
    }
}
