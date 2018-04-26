package com.instaclustr.cassandra.operator.service;

import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CassandraHealthCheckService extends AbstractScheduledService {
    static final Logger logger = LoggerFactory.getLogger(ControllerService.class);

    static final ObjectName STORAGE_SERVICE;

    static {
        try {
            STORAGE_SERVICE = ObjectName.getInstance("org.apache.cassandra.db:type=StorageService");

        } catch (final MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
    }

    private final String namespace = "default";

    private final CoreV1Api coreApi;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;

    public interface StorageServiceMBean {
        List<String> getLiveNodes();
        List<String> getUnreachableNodes();

        String getReleaseVersion();
        String getSchemaVersion();

        String getOperationMode();
    }


    @Inject
    public CassandraHealthCheckService(final CoreV1Api coreApi,  final Cache<DataCenterKey, DataCenter> dataCenterCache) {
        this.coreApi = coreApi;
        this.dataCenterCache = dataCenterCache;
    }


    @Override
    protected void runOneIteration() throws Exception {
        // TODO: maybe this would be better off querying k8s for the service endpoints rather than the pods themselves...

        logger.debug("Checking health of cassandra instances...");

        for (final Map.Entry<DataCenterKey, DataCenter> cacheEntry : dataCenterCache.asMap().entrySet()) {
            final String labelSelector = String.format("cassandra-datacenter=%s", cacheEntry.getKey().name);

            final V1PodList podList = coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, null, null, null, null);

            for (final V1Pod pod : podList.getItems()) {
                final InetAddress podIp = InetAddresses.forString(pod.getStatus().getPodIP());

                logger.debug("{} pod {} has IP {}", cacheEntry.getKey(), pod.getMetadata().getName(), podIp);

                final JMXServiceURL serviceURL = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:7199/jmxrmi", podIp));

                try (final JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, null)) {
                    final MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();

                    final StorageServiceMBean storageServiceMBean = JMX.newMBeanProxy(mBeanServerConnection, STORAGE_SERVICE, StorageServiceMBean.class);

                    final List<String> liveNodes = storageServiceMBean.getLiveNodes();
                    final List<String> unreachableNodes = storageServiceMBean.getUnreachableNodes();

                    final String operationMode = storageServiceMBean.getOperationMode();

                    final String releaseVersion = storageServiceMBean.getReleaseVersion();
                    final String schemaVersion = storageServiceMBean.getSchemaVersion();

                    logger.debug("{}", MoreObjects.toStringHelper(podIp.toString())
                            .add("liveNodes", liveNodes)
                            .add("unreachableNodes", unreachableNodes)
                            .add("operationMode", operationMode)
                            .add("releaseVersion", releaseVersion)
                            .add("schemaVersion", schemaVersion)
                            .toString()
                    );

                } catch (final Exception e) {
                    logger.warn("Failed to get Cassandra status for pod {}", podIp, e);
                }
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(0, 5, TimeUnit.MINUTES);
    }
}
