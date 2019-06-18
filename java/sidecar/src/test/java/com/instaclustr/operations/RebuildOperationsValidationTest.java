package com.instaclustr.operations;

import static com.instaclustr.sidecar.operations.OperationBindings.installOperationBindings;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildOperationRequest;
import com.instaclustr.sidecar.operations.Operation;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.validation.ValidationError;
import org.testng.annotations.Test;

public class RebuildOperationsValidationTest extends AbstractOperationsValidationTest {

    @Test
    public void validateRebuildRequests() {

        final RebuildOperationRequest allNullRebuildOperationRequest = new RebuildOperationRequest(null, null, null, null);

        final Set<ConstraintViolation<RebuildOperationRequest>> constraintViolations = validator.validate(allNullRebuildOperationRequest);
        assertTrue(constraintViolations.isEmpty());

        final RebuildOperationRequest missingKeyspaceForSpecificTokens = new RebuildOperationRequest(null,
                                                                                                     null,
                                                                                                     Stream.of(new RebuildOperationRequest.TokenRange("1", "2")).collect(toSet()),
                                                                                                     null);

        Set<ConstraintViolation<RebuildOperationRequest>> missingKeyspaceViolations = validator.validate(missingKeyspaceForSpecificTokens);
        assertTrue(!missingKeyspaceViolations.isEmpty());
        assertEquals(missingKeyspaceViolations.size(), 1);

        assertEquals(missingKeyspaceViolations.stream().findFirst().get().getMessage(), "Cannot set specificTokens without specifying a keyspace");
    }

    @Test
    public void testRebuildRequests() throws IOException {

        final Function<SidecarClient, List<SidecarClient.OperationResult<?>>> invalidRequests = client -> {

            final RebuildOperationRequest request1 = new RebuildOperationRequest(null,
                                                                                 null,
                                                                                 Stream.of(new RebuildOperationRequest.TokenRange("1", "2")).collect(toSet()),
                                                                                 null);

            return Stream.of(request1).map(client::rebuild).collect(toList());
        };

        final Pair<AtomicReference<List<SidecarClient.OperationResult<?>>>, AtomicBoolean> result = performOnRunningServer(serverService, invalidRequests);

        serverService.startAsync();

        while (!result.getRight().get()) {
        }

        result.getLeft().get().forEach(r -> assertEquals(r.response.getStatus(), BAD_REQUEST.getStatusCode()));

        final ValidationError[] validationErrors = objectMapper.readValue(SidecarClient.responseEntityToString(result.getLeft().get().get(0).response), ValidationError[].class);

        assertEquals(validationErrors[0].getMessage(), "Cannot set specificTokens without specifying a keyspace");
    }

    @Override
    protected List<Module> getModules() {
        return new ArrayList<Module>() {{
            add(new AbstractModule() {
                @Override
                protected void configure() {
                    installOperationBindings(binder(),
                                             "rebuild",
                                             RebuildOperationRequest.class,
                                             TestingRebuildOperation.class);
                }
            });
        }};
    }

    private static final class TestingRebuildOperation extends Operation<RebuildOperationRequest> {

        @Inject
        protected TestingRebuildOperation(@Assisted final RebuildOperationRequest request) {
            super(request);
        }

        @Override
        protected void run0() throws Exception {
            System.out.println("rebuild run");
        }
    }

}
