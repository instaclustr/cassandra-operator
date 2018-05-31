package com.instaclustr.cassandra.operator.model.key;

import com.instaclustr.cassandra.operator.model.Cluster;
import io.kubernetes.client.models.V1ObjectMeta;

public class ClusterKey extends Key<Cluster> {
    public ClusterKey(final String namespace, final String name) {
        super(name, namespace);
    }

    public static ClusterKey forCluster(final Cluster cluster) {
        final V1ObjectMeta metadata = cluster.getMetadata();

        return new ClusterKey(metadata.getNamespace(), metadata.getName());
    }
}
