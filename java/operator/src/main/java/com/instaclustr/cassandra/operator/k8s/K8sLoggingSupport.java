package com.instaclustr.cassandra.operator.k8s;

import com.instaclustr.cassandra.operator.model.key.Key;
import com.instaclustr.slf4j.MDC;
import io.kubernetes.client.models.V1ObjectMeta;

public class K8sLoggingSupport {
    private K8sLoggingSupport() {}

    public static String namespacedName(final V1ObjectMeta meta) {
        return meta.getNamespace() + "/" + meta.getName();
    }


    public static MDC.MDCCloseable putNamespacedName(final String key, final V1ObjectMeta meta) {
        return MDC.put(key, namespacedName(meta));
    }

    public static MDC.MDCCloseable putNamespacedName(final String key, final Key<?> objectKey) {
        return MDC.put(key, key.toString());
    }
}
