package com.instaclustr.cassandra.backup.impl.restore;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.backup.impl.commitlog.RestoreCommitLogsOperation;
import com.instaclustr.cassandra.backup.impl.commitlog.RestoreCommitLogsOperationRequest;

public class RestoreModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "restore",
                                 RestoreOperationRequest.class,
                                 RestoreOperation.class);

        installOperationBindings(binder(),
                                 "commitlog-restore",
                                 RestoreCommitLogsOperationRequest.class,
                                 RestoreCommitLogsOperation.class);
    }
}
