package com.instaclustr.cassandra.operator.model.key;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta2StatefulSet;

public class StatefulSetKey extends Key<V1beta2StatefulSet> {
    public StatefulSetKey(final String name, final String namespace) {
        super(name, namespace);
    }

    public static StatefulSetKey forStatefulSet(final V1beta2StatefulSet statefulSet) {
        final V1ObjectMeta metadata = statefulSet.getMetadata();

        return new StatefulSetKey(metadata.getNamespace(), metadata.getName());
    }
}
