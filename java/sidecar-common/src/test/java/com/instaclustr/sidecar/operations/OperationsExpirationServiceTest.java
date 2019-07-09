package com.instaclustr.sidecar.operations;

import static com.instaclustr.sidecar.operations.Operation.State.RUNNING;
import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class OperationsExpirationServiceTest {

    @Inject
    OperationsService operationsService;

    @Inject
    OperationsExpirationService operationsExpirationService;

    private final Operation testingOperation = new TestingOperation(new TestingRequest());

    static class TestingRequest extends OperationRequest {
    }

    static class TestingOperation extends Operation<TestingRequest> {

        @Inject
        TestingOperation(@Assisted final TestingRequest request) {
            super(request);
        }

        @Override
        protected void run0() throws Exception {
            Thread.sleep(5000);
        }
    }

    enum TestingOperationType implements OperationType {

        TESTING;
    }

    @BeforeTest
    public void setup() {
        final Injector injector = Guice.createInjector(
                new OperationsModule(3),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        installOperationBindings(binder(),
                                                 TestingOperationType.TESTING,
                                                 TestingRequest.class,
                                                 TestingOperation.class);
                    }
                }
        );

        injector.injectMembers(this);

        operationsExpirationService.startAsync().awaitRunning();
    }

    @Test
    public void testOperationsExpiration() throws InterruptedException {

        operationsService.submitOperation(testingOperation);

        // after two seconds, operation is still running
        Thread.sleep(2000);
        final Optional<Operation> submittedOperation = operationsService.operation(testingOperation.id);
        assertTrue(submittedOperation.isPresent());

        // after another two seconds, service is still running and it was not expired as expiration runs every three seconds
        Thread.sleep(2000);
        assertTrue(operationsService.operation(testingOperation.id).isPresent());
        assertSame(submittedOperation.get().state, RUNNING);

        Thread.sleep(5000);
        // after 5 seconds (9 in total from start of the operation submission), operation is expired
        assertFalse(operationsService.operation(testingOperation.id).isPresent());
    }
}
