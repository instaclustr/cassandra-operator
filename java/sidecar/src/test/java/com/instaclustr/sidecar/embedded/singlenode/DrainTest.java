package com.instaclustr.sidecar.embedded.singlenode;

import static com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState.DRAINED;
import static com.instaclustr.operations.Operation.State.COMPLETED;
import static org.testng.Assert.assertEquals;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.instaclustr.sidecar.embedded.AbstractCassandraSidecarTest;
import org.testng.annotations.Test;

public class DrainTest extends AbstractCassandraSidecarTest {

    @Test(expectedExceptions = AllNodesFailedException.class)
    public void drainTest() {
        sidecarClient.waitForState(sidecarClient.drain(), COMPLETED);

        assertEquals(sidecarClient.getStatus().status.getNodeState(), DRAINED);

        // this should fail because we can not do anyting cql-ish once a node is drained
        dbHelper.createTable(keyspaceName, tableName);
    }
}
