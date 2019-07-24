package com.instaclustr.cassandra.sidecar.operations.backup;

import java.net.URI;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.sidecar.operations.OperationRequest;

@SuppressWarnings("WeakerAccess")
public class BackupOperationRequest extends OperationRequest {
    public final URI destinationUri;
    public final String snapshotName;
    public final Set<String> keyspaces;

    @JsonCreator
    private BackupOperationRequest(@JsonProperty("destinationUri") final URI destinationUri,
                                   @JsonProperty("snapshotName") final String snapshotName,
                                   @JsonProperty("keyspaces") final Set<String> keyspaces) {
        this.destinationUri = destinationUri;
        this.snapshotName = snapshotName;
        this.keyspaces = keyspaces;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("destinationUri", destinationUri)
                .add("snapshotName", snapshotName)
                .add("keyspaces", keyspaces)
                .toString();
    }
}
