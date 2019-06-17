package com.instaclustr.sidecar.cassandra.operations.backup;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class BackupsModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),"backup", BackupOperationRequest.class, BackupOperation.class);
    }
}
