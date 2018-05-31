package jmx.org.apache.cassandra.streaming;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.util.Set;

@java.lang.SuppressWarnings("squid:S1214")
public interface StreamManagerMBean extends NotificationEmitter
{
    public static final String OBJECT_NAME = "org.apache.cassandra.net:type=StreamManager";

    /**
     * Returns the current state of all ongoing streams.
     */
    Set<CompositeData> getCurrentStreams();
}
