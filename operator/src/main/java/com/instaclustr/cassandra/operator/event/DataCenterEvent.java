package com.instaclustr.cassandra.operator.event;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.model.DataCenter;

public abstract class DataCenterEvent {
    public DataCenter dataCenter;

    protected DataCenterEvent(final DataCenter dataCenter) {
        this.dataCenter = dataCenter;
    }

    public interface Factory extends WatchEvent.Factory<DataCenter> {
        Added createAddedEvent(final DataCenter dataCenter);
        Modified createModifiedEvent(@Assisted("old") final DataCenter oldDataCenter, @Assisted("new") final DataCenter newDataCenter);
        Deleted createDeletedEvent(final DataCenter dataCenter);
    }

    public static class Added extends DataCenterEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final DataCenter dataCenter) {
            super(dataCenter);
        }
    }


    public static class Modified extends DataCenterEvent implements WatchEvent.Modified {
        @Inject
        public Modified(@Assisted("old") final DataCenter oldDataCenter, @Assisted("new") final DataCenter newDataCenter) {
            super(newDataCenter);
        }
    }

    public static class Deleted extends DataCenterEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final DataCenter dataCenter) {
            super(dataCenter);
        }
    }

}
