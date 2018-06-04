package com.instaclustr.cassandra.operator.event;

import javax.annotation.Nullable;

public class WatchEvent {

    public interface Factory<T> {
        Added createAddedEvent(final T object);
        Modified createModifiedEvent(@Nullable final T oldObject, final T newObject);
        Deleted createDeletedEvent(final T object);
    }

    public interface Added {}
    public interface Modified {}
    public interface Deleted {}
}
