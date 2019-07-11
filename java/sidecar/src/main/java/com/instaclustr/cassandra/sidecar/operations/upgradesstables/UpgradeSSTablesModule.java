package com.instaclustr.cassandra.sidecar.operations.upgradesstables;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;

import com.google.inject.AbstractModule;
import com.instaclustr.cassandra.sidecar.operations.CassandraOperationType;

public class UpgradeSSTablesModule extends AbstractModule {
    @Override
    protected void configure() {
        installOperationBindings(binder(),
                                 CassandraOperationType.UPGRADESSTABLES,
                                 UpgradeSSTablesOperationRequest.class,
                                 UpgradeSSTablesOperation.class);
    }
}
