package com.instaclustr.cassandra.operator.model.key;

import com.instaclustr.cassandra.operator.model.DataCenter;
import io.kubernetes.client.models.V1ObjectMeta;

public class DataCenterKey extends Key<DataCenter> {
    public DataCenterKey(final String namespace, final String name) {
        super(name, namespace);
    }

    public static DataCenterKey forDataCenter(final DataCenter dataCenter) {
        final V1ObjectMeta metadata = dataCenter.getMetadata();

        return new DataCenterKey(metadata.getNamespace(), metadata.getName());
    }
}
