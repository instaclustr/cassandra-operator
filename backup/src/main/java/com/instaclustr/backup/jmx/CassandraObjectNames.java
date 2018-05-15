package com.instaclustr.backup.jmx;


import com.google.common.base.Throwables;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public final class CassandraObjectNames {
    public static final ObjectName STORAGE_SERVICE;
    public static final ObjectName FAILURE_DETECTOR;
    public static final ObjectName MESSAGING_SERVICE;
    public static final ObjectName COMPACTION_MANAGER;
    public static final ObjectName AES_OBJECT_NAME;
    public static final ObjectName STREAM_MANAGER;

    static {
        try {
            STORAGE_SERVICE = ObjectName.getInstance("org.apache.cassandra.db:type=StorageService");
            FAILURE_DETECTOR = ObjectName.getInstance("org.apache.cassandra.net:type=FailureDetector");
            MESSAGING_SERVICE = ObjectName.getInstance("org.apache.cassandra.net:type=MessagingService");
            COMPACTION_MANAGER = ObjectName.getInstance("org.apache.cassandra.db:type=CompactionManager");
            AES_OBJECT_NAME = ObjectName.getInstance("org.apache.cassandra.internal:type=AntiEntropySessions");
            STREAM_MANAGER = ObjectName.getInstance("org.apache.cassandra.net:type=StreamManager");
        } catch (final MalformedObjectNameException e) {
            throw Throwables.propagate(e);
        }
    }


}
