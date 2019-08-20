package jmx.org.apache.cassandra;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public final class CassandraObjectNames {
    public static final ObjectName GOSSIPER_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.net:type=Gossiper");
    public static final ObjectName FAILURE_DETECTOR_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.net:type=FailureDetector");
    public static final ObjectName ENDPOINT_SNITCH_INFO_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.db:type=EndpointSnitchInfo");
    public static final ObjectName STORAGE_SERVICE_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.db:type=StorageService");
    public static final ObjectName MESSAGING_SERVICE = ObjectNames.create("org.apache.cassandra.net:type=MessagingService");
    public static final ObjectName COMPACTION_MANAGER = ObjectNames.create("org.apache.cassandra.db:type=CompactionManager");
    public static final ObjectName AES_OBJECT_NAME = ObjectNames.create("org.apache.cassandra.internal:type=AntiEntropySessions");
    public static final ObjectName STREAM_MANAGER = ObjectNames.create("org.apache.cassandra.net:type=StreamManager");

    private CassandraObjectNames() {}

    private static class ObjectNames {
        private ObjectNames() {}

        public static ObjectName create(final String name) {
            try {
                return ObjectName.getInstance(name);

            } catch (final MalformedObjectNameException e) {
                throw new IllegalStateException(e);
            }
        }

        public static ObjectName format(final String nameFormat, final Object... args) {
            return create(String.format(nameFormat, args));
        }
    }
}
