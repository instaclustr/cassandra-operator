package com.instaclustr.cassandra.operator.model.key;

import com.google.common.base.MoreObjects;

import java.util.Objects;

public abstract class Key<T> {
    public final String namespace;
    public final String name;

    public Key(final String name, final String namespace) {
        this.name = name;
        this.namespace = namespace;
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Key that = (Key) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespace", namespace)
                .add("name", name)
                .toString();
    }
}
