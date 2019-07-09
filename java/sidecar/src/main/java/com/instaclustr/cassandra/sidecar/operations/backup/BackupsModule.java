package com.instaclustr.cassandra.sidecar.operations.backup;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;

public class BackupsModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 CassandraOperationType.BACKUP,
                                 BackupOperationRequest.class,
                                 BackupOperation.class);
    }
}
