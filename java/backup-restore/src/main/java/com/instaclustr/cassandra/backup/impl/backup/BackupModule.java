package com.instaclustr.cassandra.backup.impl.backup;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

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
