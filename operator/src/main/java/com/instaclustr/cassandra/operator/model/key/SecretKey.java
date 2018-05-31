package com.instaclustr.cassandra.operator.model.key;

import com.instaclustr.cassandra.operator.model.DataCenter;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;

public class SecretKey extends Key<DataCenter> {
    public SecretKey(final String namespace, final String name) {
        super(name, namespace);
    }

    public static SecretKey forConfigMap(final V1ConfigMap configMap) {
        final V1ObjectMeta metadata = configMap.getMetadata();

        return new SecretKey(metadata.getNamespace(), metadata.getName());
    }
}
