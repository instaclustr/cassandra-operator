package com.instaclustr.cassandra.backup.cli;

import static com.instaclustr.cassandra.backup.cli.BackupRestoreCLI.init;
import static com.instaclustr.picocli.CLIApplication.execute;
import static org.awaitility.Awaitility.await;

import com.google.inject.Inject;
import com.instaclustr.cassandra.backup.cli.BackupRestoreCLI.CLIJarManifestVersionProvider;
import com.instaclustr.cassandra.backup.impl.restore.RestoreOperationRequest;
import com.instaclustr.sidecar.operations.Operation;
import com.instaclustr.sidecar.operations.OperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "restore",
        mixinStandardHelpOptions = true,
        description = "Restore the Cassandra data on this node to a specified point-in-time.",
        sortOptions = false,
        versionProvider = CLIJarManifestVersionProvider.class
)
public class RestoreApplication implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RestoreApplication.class);

    @CommandLine.Mixin
    private RestoreOperationRequest request;

    @Inject
    private OperationsService operationsService;

    public static void main(String[] args) {
        System.exit(execute(new RestoreApplication(), args));
    }

    @Override
    public void run() {
        init(this, null, request, logger);

        final Operation operation = operationsService.submitOperationRequest(request);

        await().forever().until(() -> operation.state.isTerminalState());
    }
}
