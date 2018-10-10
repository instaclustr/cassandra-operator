package com.instaclustr.cassandra.operator.jmx;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.net.InetAddresses;
import io.kubernetes.client.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CassandraConnectionFactory {
    private static final Logger logger = LoggerFactory.getLogger(CassandraConnectionFactory.class);

    private final LoadingCache<InetAddress, CassandraConnection> connectionCache = CacheBuilder.<InetAddress, CassandraConnection>newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .removalListener((RemovalNotification<InetAddress, CassandraConnection> notification) -> {
                try {
                    notification.getValue().close();

                } catch (final IOException e) {
                    logger.warn("Failed to close JMX connection to {}.", notification.getKey(), e);
                }
            })
            .build(new CacheLoader<InetAddress, CassandraConnection>() {
                @Override
                public CassandraConnection load(@Nonnull final InetAddress key) throws Exception {
                    final JMXServiceURL serviceURL = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:7199/jmxrmi", InetAddresses.toAddrString(key)));

                    logger.debug("Establishing JMX connection to {} ({})", key, serviceURL);

                    final JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, null);

                    // observe connections and when they fail/close, invalidate the cache
                    jmxConnector.addConnectionNotificationListener((notification, handback) -> {
                        if (!(notification instanceof JMXConnectionNotification)) {
                            return;
                        }

                        final JMXConnectionNotification connectionNotification = (JMXConnectionNotification) notification;
                        switch (connectionNotification.getType()) {
                            case JMXConnectionNotification.FAILED:
                                logger.warn("JMX connection to {} unexpectedly failed. {}", key, notification);
                                connectionCache.invalidate(key);

                                break;

                            case JMXConnectionNotification.CLOSED:
                                logger.debug("JMX connection to {} closed.", key);
                                connectionCache.invalidate(key);

                                break;
                        }
                    }, null, null);

                    return new CassandraConnection(jmxConnector);
                }
            });

    public CassandraConnection connectionForAddress(final InetAddress address) {
        try {
            return connectionCache.get(address);
        } catch (final ExecutionException e) {
            logger.warn("Unable to connect to address {}", address, e);
            throw new RuntimeException(e); //TODO: handle better
        }
    }

    public CassandraConnection connectionForPod(final V1Pod pod) {
        return connectionForAddress(InetAddresses.forString(pod.getStatus().getPodIP()));
    }

}
