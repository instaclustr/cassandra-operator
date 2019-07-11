package com.instaclustr.cassandra.sidecar.operations.backup;

import javax.inject.Inject;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.sidecar.operations.Operation;


@SuppressWarnings("WeakerAccess")
public class BackupOperation extends Operation<BackupOperationRequest> {
    public String currentFile = null;

    @Inject
    public BackupOperation(@Assisted final BackupOperationRequest request) {
        super(request);
    }

    @Override
    protected void run0() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(1000);

            this.progress = ((float) i) / 100.f;
            this.currentFile = String.format("Uploading file %d of %d to %s", i, 100, this.request.destinationUri);
        }
    }
}
