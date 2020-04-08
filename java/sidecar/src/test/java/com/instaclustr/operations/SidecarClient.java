package com.instaclustr.operations;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperation;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupOperation;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissionOperation;
import com.instaclustr.cassandra.sidecar.operations.decommission.DecommissionOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.drain.DrainOperation;
import com.instaclustr.cassandra.sidecar.operations.drain.DrainOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.flush.FlushOperation;
import com.instaclustr.cassandra.sidecar.operations.flush.FlushOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildOperation;
import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.refresh.RefreshOperation;
import com.instaclustr.cassandra.sidecar.operations.refresh.RefreshOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.restart.RestartOperation;
import com.instaclustr.cassandra.sidecar.operations.restart.RestartOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubOperation;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesOperation;
import com.instaclustr.cassandra.sidecar.operations.upgradesstables.UpgradeSSTablesOperationRequest;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status;
import com.instaclustr.operations.Operation.State;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.glassfish.jersey.server.ResourceConfig;
import org.testng.Assert;

public class SidecarClient implements Closeable {

    private final String rootUrl;
    private final Client client;
    private final WebTarget statusWebTarget;
    private final WebTarget operationsWebTarget;
    private final int port;

    private SidecarClient(final Builder builder, final ResourceConfig resourceConfig) {
        client = ClientBuilder.newBuilder().withConfig(resourceConfig).build();

        rootUrl = String.format("http://%s:%s", builder.hostname, builder.port);

        statusWebTarget = client.target(String.format("%s/status", rootUrl));
        operationsWebTarget = client.target(String.format("%s/operations", rootUrl));
        port = builder.port;
    }

    public StatusResult getStatus() {
        final Response response = statusWebTarget.request(APPLICATION_JSON).get();

        try {
            final Status status = response.readEntity(Status.class);
            return new StatusResult(status, response);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int getPort() {
        return port;
    }

    public Operation getOperation(final UUID operationId) {
        return operationsWebTarget.path(operationId.toString()).request(APPLICATION_JSON).get(Operation.class);
    }

    public <OPERATION_TYPE> OPERATION_TYPE getOperation(final UUID operationId, final Class<OPERATION_TYPE> operationType) {
        return operationsWebTarget.path(operationId.toString()).request(APPLICATION_JSON).get(operationType);
    }

    public OperationResult<CleanupOperation> cleanup(final CleanupOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, CleanupOperation.class);
    }

    public OperationResult<DecommissionOperation> decommission(final DecommissionOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, DecommissionOperation.class);
    }

    public OperationResult<DecommissionOperation> decommission() {
        return decommission(new DecommissionOperationRequest());
    }

    public OperationResult<RebuildOperation> rebuild(final RebuildOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, RebuildOperation.class);
    }

    public OperationResult<ScrubOperation> scrub(final ScrubOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, ScrubOperation.class);
    }

    public OperationResult<UpgradeSSTablesOperation> upgradeSSTables(final UpgradeSSTablesOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, UpgradeSSTablesOperation.class);
    }

    public OperationResult<BackupOperation> backup(final BackupOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, BackupOperation.class);
    }

    public OperationResult<DrainOperation> drain(final DrainOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, DrainOperation.class);
    }

    public OperationResult<DrainOperation> drain() {
        return drain(new DrainOperationRequest());
    }

    public OperationResult<RestartOperation> restart(final RestartOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, RestartOperation.class);
    }

    public OperationResult<RefreshOperation> refresh(final RefreshOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, RefreshOperation.class);
    }

    public OperationResult<FlushOperation> flush(final FlushOperationRequest operationRequest) {
        return performOperationSubmission(operationRequest, FlushOperation.class);
    }

    public Collection<Operation> getOperations() {
        return getOperations(ImmutableSet.of(), ImmutableSet.of());
    }

    public Collection<Operation> getOperations(final Set<String> operations, final Set<Operation.State> states) {

        WebTarget webTarget = client.target(String.format("%s/operations", rootUrl));

        if (operations != null && !operations.isEmpty()) {
            webTarget = webTarget.queryParam("type", operations.toArray());
        }

        if (states != null && !states.isEmpty()) {
            webTarget = webTarget.queryParam("state", states.stream().map(Operation.State::name).toArray());
        }

        return Arrays.asList(webTarget.request(APPLICATION_JSON).get(Operation[].class));
    }

    public static String responseEntityToString(final Response response) throws IOException {
        return CharStreams.toString(new InputStreamReader((InputStream) response.getEntity()));
    }

    private <T extends OperationRequest, O extends Operation> OperationResult<O> performOperationSubmission(final T operationRequest, Class<O> operationClass) {

        final Response post = operationsWebTarget.request(APPLICATION_JSON).post(Entity.json(operationRequest));

        if (post.getStatusInfo().toEnum() != Response.Status.CREATED) {
            return new OperationResult<O>(null, post);
        }

        return new OperationResult<O>(post.readEntity(operationClass), post);
    }

    @Override
    public void close() {
        client.close();
    }

    public static final class Builder {

        private String hostname = "localhost";

        private int port = 8080;

        public Builder withInetSocketAddress(final InetSocketAddress inetSocketAddress) {
            withHostname(inetSocketAddress.getHostName());
            withPort(inetSocketAddress.getPort());
            return this;
        }

        public Builder withHostname(final String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder withPort(final int port) {
            this.port = port;
            return this;
        }

        public SidecarClient build(final ResourceConfig resourceConfig) {
            return new SidecarClient(this, resourceConfig);
        }
    }

    public static class StatusResult {

        public final Status status;
        public final Response response;

        public StatusResult(final Status status, final Response response) {
            this.status = status;
            this.response = response;
        }
    }

    public static class OperationResult<O extends Operation> {

        public final O operation;
        public final Response response;

        public OperationResult(final O operation, final Response response) {
            this.operation = operation;
            this.response = response;
        }
    }

    public void waitForCompleted(OperationResult<? extends Operation<?>> operationResult) {
        waitForState(operationResult, State.COMPLETED);
    }

    public void waitForFailed(OperationResult<? extends Operation<?>> operationResult) {
        waitForState(operationResult, State.FAILED);
    }

    public void waitForPending(OperationResult<? extends Operation<?>> operationResult) {
        waitForState(operationResult, State.PENDING);
    }

    public void waitForRunning(OperationResult<? extends Operation<?>> operationResult) {
        waitForState(operationResult, State.RUNNING);
    }

    public void waitForState(OperationResult<? extends Operation<?>> operationResult, State state) {

        Awaitility.await().atMost(Duration.FIVE_MINUTES).until(() -> {

            final State returnedState = getOperation(operationResult.operation.id).state;

            if (state == State.FAILED && returnedState == state) {
                return true;
            }

            if (state == State.PENDING && returnedState == state) {
                return true;
            }

            if (state == State.RUNNING && returnedState == state) {
                return true;
            }

            if (returnedState == State.FAILED) {
                Assert.fail("Operation has failed.");
                return false;
            } else if (returnedState == State.PENDING || returnedState == State.RUNNING) {
                return false;
            } else {
                return returnedState == state;
            }
        });
    }
}
