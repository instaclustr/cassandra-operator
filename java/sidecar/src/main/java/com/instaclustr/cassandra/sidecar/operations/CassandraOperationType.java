package com.instaclustr.cassandra.sidecar.operations;

import com.instaclustr.sidecar.operations.OperationType;

public enum CassandraOperationType implements OperationType {
    CLEANUP,
    BACKUP,
    DECOMMISSION,
    REBUILD,
    SCRUB,
    UPGRADESSTABLES;
}
