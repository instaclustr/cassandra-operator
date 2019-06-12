package com.instaclustr.cassandra.sidecar.operations.backup;

import com.google.inject.AbstractModule;

import static com.instaclustr.cassandra.sidecar.operations.OperationBindings.installOperationBindings;

public class BackupsModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),"backup", BackupOperationRequest.class, BackupOperation.class);
    }
}
