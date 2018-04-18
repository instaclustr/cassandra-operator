package com.instaclustr.cassandra.operator.event;

public final class WatchEvent {

    public interface Factory<T> {
        Added createAddedEvent(final T object);
        Modified createModifiedEvent(final T oldObject, final T newObject);
        Deleted createDeletedEvent(final T object);
    }

    public interface Added {}
    public interface Modified {}
    public interface Deleted {}
}
