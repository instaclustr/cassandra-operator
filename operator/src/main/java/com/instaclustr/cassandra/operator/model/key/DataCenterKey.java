package com.instaclustr.cassandra.operator.model.key;

import com.google.common.base.MoreObjects;
import com.instaclustr.cassandra.operator.model.DataCenter;
import io.kubernetes.client.models.V1ObjectMeta;

import java.util.Objects;

public class DataCenterKey {
    public final String namespace, name;

    public DataCenterKey(final String namespace, final String name) {
        this.namespace = namespace;
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataCenterKey that = (DataCenterKey) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespace", namespace)
                .add("name", name)
                .toString();
    }

    public static DataCenterKey forDataCenter(final DataCenter dataCenter) {
        final V1ObjectMeta metadata = dataCenter.getMetadata();

        return new DataCenterKey(metadata.getNamespace(), metadata.getName());
    }
}
