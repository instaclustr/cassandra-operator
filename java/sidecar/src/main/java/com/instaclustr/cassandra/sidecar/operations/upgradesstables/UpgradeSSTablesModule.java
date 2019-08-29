package com.instaclustr.cassandra.sidecar.operations.upgradesstables;

import static com.instaclustr.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;

public class UpgradeSSTablesModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 "upgradesstables",
                                 UpgradeSSTablesOperationRequest.class,
                                 UpgradeSSTablesOperation.class);
    }
}
