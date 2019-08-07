package com.instaclustr.cassandra.backup.cli;

import static com.instaclustr.cassandra.backup.cli.BackupRestoreCLI.init;
import static com.instaclustr.picocli.CLIApplication.execute;
import static org.awaitility.Awaitility.await;

import com.google.inject.Inject;
import com.instaclustr.cassandra.backup.cli.BackupRestoreCLI.CLIJarManifestVersionProvider;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.sidecar.operations.Operation;
import com.instaclustr.sidecar.operations.OperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "backup",
        mixinStandardHelpOptions = true,
        description = "Take a snapshot of this nodes Cassandra data and upload it to remote storage. " +
                "Defaults to a snapshot of all keyspaces and their column families, " +
                "but may be restricted to specific keyspaces or a single column-family.",
        sortOptions = false,
        versionProvider = CLIJarManifestVersionProvider.class
)
public class BackupApplication implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    @CommandLine.Mixin
    private CassandraJMXSpec jmxSpec;

    @CommandLine.Mixin
    private BackupOperationRequest request;

    @Inject
    private OperationsService operationsService;

    public static void main(String[] args) {
        System.exit(execute(new BackupApplication(), args));
    }

    @Override
    public void run() {
        init(this, jmxSpec, request, logger);

        final Operation operation = operationsService.submitOperationRequest(request);

        await().forever().until(() -> operation.state.isTerminalState());
    }
}
