package com.instaclustr.cassandra.backup.impl.backup;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.backup.impl.commitlog.BackupCommitLogsOperation;
import com.instaclustr.cassandra.backup.impl.commitlog.BackupCommitLogsOperationRequest;

public class BackupModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "backup",
                                 BackupOperationRequest.class,
                                 BackupOperation.class);

        installOperationBindings(binder(),
                                 "commitlog-backup",
                                 BackupCommitLogsOperationRequest.class,
                                 BackupCommitLogsOperation.class);
    }
}
