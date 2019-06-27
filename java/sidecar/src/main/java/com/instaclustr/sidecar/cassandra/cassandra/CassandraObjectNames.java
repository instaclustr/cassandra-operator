package com.instaclustr.sidecar.cassandra.cassandra;

import com.instaclustr.jmx.ObjectNames;

import javax.management.ObjectName;

public final class CassandraObjectNames {
    public static final ObjectName GOSSIPER_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.net:type=Gossiper");
    public static final ObjectName FAILURE_DETECTOR_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.net:type=FailureDetector");
    public static final ObjectName ENDPOINT_SNITCH_INFO_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.db:type=EndpointSnitchInfo");
    public static final ObjectName STORAGE_SERVICE_MBEAN_NAME = ObjectNames.create("org.apache.cassandra.db:type=StorageService");

    private CassandraObjectNames() {}
}
