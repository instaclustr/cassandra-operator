package com.instaclustr.cassandra.sidecar.operation;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.instaclustr.cassandra.sidecar.cassandra.CassandraModule;
import com.instaclustr.cassandra.sidecar.model.operation.BackupOperation;
import com.instaclustr.cassandra.sidecar.model.operation.DecommissionOperation;
import com.instaclustr.cassandra.sidecar.model.operation.OperationState;
import com.instaclustr.cassandra.sidecar.model.result.DecommissionResult;
import com.instaclustr.cassandra.sidecar.model.result.OperationResult;
import com.instaclustr.cassandra.sidecar.operation.task.BackupTask;
import com.instaclustr.cassandra.sidecar.operation.task.DecommissionTask;
import com.instaclustr.cassandra.sidecar.operation.task.TaskFactory;
import com.instaclustr.cassandra.sidecar.resource.OperationsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OperationExecutorTest {

    private static final Logger logger = LoggerFactory.getLogger(OperationExecutorTest.class);

    private CassandraModule cassandraModule;

    private TaskFactory taskFactory;

    private class DummyDecommissionTask extends DecommissionTask {

        public DummyDecommissionTask(DecommissionOperation operation) {
            super(operation);
        }

        @Override
        protected void executeTask(DecommissionOperation operation, DecommissionResult decommissionResult) throws Exception {
            // normally we would have here storageServiceMBean doing decommission task but for sake of testing whole operations implementation we fake it here
            // in more complicated scenarios, we can fill in the result and accommodate task execution based on 'operation' parameter which came from the caller
            // all fields for a result in its base Operation class are treated automatically without any need to take care of them here
            // so end-user can focus only on what is specific to his specific result.

            Thread.sleep(5000);
        }
    }

    @BeforeClass
    public void setup() {
        // mock mBeanServerConnection if necessary
        cassandraModule = new CassandraModule(null);

        taskFactory = new TaskFactory() {
            @Override
            public DecommissionTask createDecommissionTask(DecommissionOperation decommissionOperation) {
                return new DummyDecommissionTask(decommissionOperation);
            }

            @Override
            public BackupTask createBackupTask(BackupOperation backupOperation) {
                return null;
            }
        };
    }

    @Test
    public void operationResourceConcurrentSubmissionsTest() {
//
//        final OperationExecutor executor = new DefaultOperationExecutor(null, cassandraModule.operationsExecutorService());
//
//        final OperationsResource operationsResource = new OperationsResource(executor, taskFactory);
//
//        final DecommissionOperation decommissionOperation = new DecommissionOperation();
//
//        // this will be executed in the background and returns before that operation is finished
//        final Response firstResponse = operationsResource.executeOperation(decommissionOperation);
//
//        // check location is there hence operation was submitted
//        final String location = firstResponse.getHeaderString("Location");
//        assertNotNull(location);
//        assertEquals(location, "/operations/" + decommissionOperation.getId());
//        assertEquals(firstResponse.getStatus(), Response.Status.CREATED.getStatusCode());
//
//        // get that operation and check it is running
//
//        final Response runningOperation = operationsResource.getOperation(decommissionOperation.getId());
//        assertEquals(runningOperation.getStatus(), Response.Status.OK.getStatusCode());
//
//        assertNotNull(runningOperation.getEntity());
//        final Object entity = runningOperation.getEntity();
//
//        assertTrue(entity instanceof OperationResult);
//        assertEquals(((OperationResult) entity).getOperationState(), OperationState.RUNNING);
//
//        // get all operations and check there is our decommission which is running
//
//        final Response operations = operationsResource.getOperations();
//        assertEquals(operations.getStatus(), Response.Status.OK.getStatusCode());
//
//        assertNotNull(operations.getEntity());
//        final Stream operationsEntity = (Stream) operations.getEntity();
//
//        final List<OperationResult> entityList = (List<OperationResult>) operationsEntity.collect(Collectors.toList());
//        assertEquals(entityList.size(), 1);
//        assertEquals(entityList.get(0).getOperationState(), OperationState.RUNNING);
//
//        // try to do the second decommissioning while the first one is still running
//
//        final Response secondResponse = operationsResource.executeOperation(new DecommissionOperation());
//        assertEquals(secondResponse.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
//        assertEquals(secondResponse.getStatusInfo().getReasonPhrase(), "Can not submit operation of type DECOMMISSION. Same operation is already running.");
    }

    @Test
    public void taskSubmissionTest() throws Exception {
//
//        final DecommissionTask decommissionTask = taskFactory.createDecommissionTask(new DecommissionOperation());
//
//        final OperationExecutor executor = new DefaultOperationExecutor(null, cassandraModule.operationsExecutorService());
//
//        Future<DecommissionResult> decommissionResultFuture = executor.submit(decommissionTask);
//
//        assertEquals(decommissionTask.getOperationResult().getOperationState(), OperationState.SUBMITTED);
//
//        Thread.sleep(2000);
//
//        assertEquals(decommissionTask.getOperationResult().getOperationState(), OperationState.RUNNING);
//
//        DecommissionResult decommissionResult = decommissionResultFuture.get(); // blocks
//
//        assertEquals(decommissionTask.getOperationResult().getOperationState(), OperationState.FINISHED);
//
//        logger.info("DecommissionResult {}", decommissionResult);
    }
}
