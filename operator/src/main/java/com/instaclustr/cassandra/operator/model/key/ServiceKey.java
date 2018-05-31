package com.instaclustr.cassandra.operator.model.key;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;

public class ServiceKey extends Key<V1Service> {
    public ServiceKey(final String namespace, final String name) {
        super(name, namespace);
    }

    public static ServiceKey forCluster(final V1Service service) {
        final V1ObjectMeta metadata = service.getMetadata();

        return new ServiceKey(metadata.getNamespace(), metadata.getName());
    }
}
