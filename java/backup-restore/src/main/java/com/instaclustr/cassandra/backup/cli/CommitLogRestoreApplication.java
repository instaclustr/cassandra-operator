package com.instaclustr.cassandra.backup.cli;

import static com.instaclustr.cassandra.backup.cli.BackupRestoreCLI.init;
import static com.instaclustr.picocli.CLIApplication.execute;
import static com.instaclustr.picocli.JarManifestVersionProvider.logCommandVersionInformation;
import static org.awaitility.Awaitility.await;

import com.google.inject.Inject;
import com.instaclustr.cassandra.backup.impl.restore.RestoreCommitLogsOperationRequest;
import com.instaclustr.sidecar.operations.Operation;
import com.instaclustr.sidecar.operations.OperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "commitlog-restore",
         mixinStandardHelpOptions = true,
         description = "Restores archived commit logs to node.",
         sortOptions = false,
         versionProvider = BackupRestoreCLI.class
)
public class CommitLogRestoreApplication implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CommitLogRestoreApplication.class);

    @Spec
    private CommandSpec spec;

    @Mixin
    private RestoreCommitLogsOperationRequest request;

    @Inject
    private OperationsService operationsService;

    public static void main(String[] args) {
        System.exit(execute(new CommitLogRestoreApplication(), args));
    }

    @Override
    public void run() {
        logCommandVersionInformation(spec);

        init(this, null, request, logger);

        final Operation operation = operationsService.submitOperationRequest(request);

        await().forever().until(() -> operation.state.isTerminalState());
    }
}
