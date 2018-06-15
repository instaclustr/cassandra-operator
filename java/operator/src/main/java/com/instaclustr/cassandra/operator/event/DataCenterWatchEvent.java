package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.model.DataCenter;

import javax.annotation.Nullable;
import javax.inject.Inject;

@SuppressWarnings("WeakerAccess")
public abstract class DataCenterWatchEvent extends WatchEvent {
    public DataCenter dataCenter;

    protected DataCenterWatchEvent(final DataCenter dataCenter) {
        this.dataCenter = dataCenter;
    }

    public interface Factory extends WatchEvent.Factory<DataCenter> {
        Added createAddedEvent(final DataCenter dataCenter);
        Modified createModifiedEvent(@Nullable @Assisted("old") final DataCenter oldDataCenter, @Assisted("new") final DataCenter newDataCenter);
        Deleted createDeletedEvent(final DataCenter dataCenter);
    }

    public static class Added extends DataCenterWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final DataCenter dataCenter) {
            super(dataCenter);
        }
    }

    public static class Modified extends DataCenterWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final DataCenter oldDataCenter;

        @Inject
        public Modified(@Nullable @Assisted("old") final DataCenter oldDataCenter, @Assisted("new") final DataCenter newDataCenter) {
            super(newDataCenter);
            this.oldDataCenter = oldDataCenter;
        }
    }

    public static class Deleted extends DataCenterWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final DataCenter dataCenter) {
            super(dataCenter);
        }
    }
}
