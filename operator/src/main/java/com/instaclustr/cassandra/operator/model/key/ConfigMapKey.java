package com.instaclustr.cassandra.operator.model.key;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;

public class ConfigMapKey extends Key<V1ConfigMap> {
    public ConfigMapKey(final String namespace, final String name) {
        super(name, namespace);
    }

    public static ConfigMapKey forConfigMap(final V1ConfigMap configMap) {
        final V1ObjectMeta metadata = configMap.getMetadata();

        return new ConfigMapKey(metadata.getNamespace(), metadata.getName());
    }
}
