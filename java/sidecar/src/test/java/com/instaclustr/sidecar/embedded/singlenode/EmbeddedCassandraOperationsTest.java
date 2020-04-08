package com.instaclustr.sidecar.embedded.singlenode;

import java.util.Collections;

import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.flush.FlushOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.refresh.RefreshOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesOperationRequest;
import com.instaclustr.sidecar.embedded.AbstractCassandraSidecarTest;
import org.testng.annotations.Test;

public class EmbeddedCassandraOperationsTest extends AbstractCassandraSidecarTest {

    @Test
    public void flushTest() {
        sidecarClient.waitForCompleted(sidecarClient.flush(new FlushOperationRequest(keyspaceName, Collections.singleton(tableName))));
        sidecarClient.waitForCompleted(sidecarClient.flush(new FlushOperationRequest(keyspaceName, Collections.EMPTY_SET)));
    }

    @Test
    public void refreshTest() {
        sidecarClient.waitForCompleted(sidecarClient.refresh(new RefreshOperationRequest(keyspaceName, tableName)));
    }

    @Test
    public void cleanupTest() {
        sidecarClient.waitForCompleted(sidecarClient.cleanup(new CleanupOperationRequest(keyspaceName, Collections.singleton(tableName), 0)));
    }

    @Test
    public void scrubTest() {
        sidecarClient.waitForCompleted(sidecarClient.scrub(new ScrubOperationRequest(false, false, false, false, 0, keyspaceName, Collections.EMPTY_SET)));
    }

    @Test
    public void upgradeSSTables() {
        sidecarClient.waitForCompleted(sidecarClient.upgradeSSTables(new UpgradeSSTablesOperationRequest(keyspaceName,
                                                                                                         Collections.singleton(tableName),
                                                                                                         true,
                                                                                                         0)));
    }
}
