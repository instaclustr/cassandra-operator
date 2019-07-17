package com.instaclustr.cassandra.backup.guice;

import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.cassandra.backup.impl.restore.Restorer;

@FunctionalInterface
public interface RestorerFactory<RESTORER extends Restorer> {
    RESTORER createRestorer(final RestoreOperationRequest restoreOperationRequest);
}
