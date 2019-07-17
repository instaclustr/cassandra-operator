package com.instaclustr.cassandra.backup.impl.restore;

import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.backup.guice.RestorerFactory;

public class RestoreOperation extends BaseRestoreOperation<RestoreOperationRequest> {
    private final Map<String, RestorerFactory> restorerFactoryMap;

    @Inject
    protected RestoreOperation(final Map<String, RestorerFactory> restorerFactoryMap,
                               @Assisted final RestoreOperationRequest request) {
        super(request);
        this.restorerFactoryMap = restorerFactoryMap;
    }

    @Override
    protected void run0() throws Exception {
        final Restorer restorer = restorerFactoryMap.get(request.storageLocation.storageProvider).createRestorer(request);

        restorer.restore();

        writeConfigOptions(restorer, request.keyspaceTables.size() > 0);
    }
}
