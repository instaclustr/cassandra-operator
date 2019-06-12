package com.instaclustr.cassandra.sidecar.operations.backup;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.sidecar.operations.Operation;

import javax.inject.Inject;

@JsonTypeName("backup")
public class BackupOperation extends Operation<BackupOperationRequest> {
    @Inject
    public BackupOperation(@Assisted final BackupOperationRequest request) {
        super(request);
    }

    @Override
    protected void run0() {
    }
}
