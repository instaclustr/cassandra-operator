package com.instaclustr.cassandra.sidecar;

import com.instaclustr.cassandra.backup.cli.BackupRestoreCLI;
import com.instaclustr.picocli.CLIApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    mixinStandardHelpOptions = true,
    subcommands = {BackupRestoreCLI.class, Sidecar.class},
    versionProvider = SidecarAggregationCommand.class,
    usageHelpWidth = 128
)
public class SidecarAggregationCommand extends CLIApplication implements Runnable {

    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        main(args, true);
    }

    public static void main(String[] args, boolean exit) {
        int exitCode = execute(new CommandLine(new SidecarAggregationCommand()), args);

        if (exit) {
            System.exit(exitCode);
        }
    }

    @Override
    public String getImplementationTitle() {
        return "sidecar-aggregator";
    }

    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing required sub-command.");
    }
}
