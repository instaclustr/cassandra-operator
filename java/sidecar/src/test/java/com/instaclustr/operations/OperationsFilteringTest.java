package com.instaclustr.operations;

import static com.google.common.collect.ImmutableList.of;
import static com.instaclustr.operations.Operation.State.COMPLETED;
import static com.instaclustr.operations.Operation.State.FAILED;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.google.common.collect.ImmutableSet;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissionOperationRequest;
import com.instaclustr.operations.SidecarClient.OperationResult;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.Test;

public class OperationsFilteringTest extends AbstractSidecarTest {

    @Test
    public void operationsFilteringTest() {
        final Function<SidecarClient, List<OperationResult<?>>> requests = client -> of(client.cleanup(new CleanupOperationRequest("some_keyspace",
                                                                                                                                   null,
                                                                                                                                   0)),
                                                                                        client.decommission(new DecommissionOperationRequest()));

        final Pair<AtomicReference<List<OperationResult<?>>>, AtomicBoolean> result = performOnRunningServer(requests);

        await().atMost(1, MINUTES).until(() -> result.getRight().get());
        await().until(() -> sidecarClient.getOperations().stream().allMatch(operation -> operation.state == COMPLETED));

        assertFalse(sidecarClient.getOperations(ImmutableSet.of("cleanup"), ImmutableSet.of(COMPLETED)).isEmpty());
        assertFalse(sidecarClient.getOperations(ImmutableSet.of("decommission"), ImmutableSet.of(COMPLETED)).isEmpty());
        assertEquals(sidecarClient.getOperations(ImmutableSet.of("decommission", "cleanup"), ImmutableSet.of(COMPLETED)).size(), 2);
        assertEquals(sidecarClient.getOperations(ImmutableSet.of("decommission", "cleanup"), ImmutableSet.of(FAILED)).size(), 0);
    }
}
