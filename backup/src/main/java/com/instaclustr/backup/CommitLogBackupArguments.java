package com.instaclustr.backup;

import org.kohsuke.args4j.Option;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.nio.file.Path;

public class CommitLogBackupArguments extends CommonBackupArguments {

    public CommitLogBackupArguments(final String appName, final PrintStream stream) {
        super(appName, stream);
    }

    @Override
    public void commonBackupPrintHelp() {
        stream.println("Upload archived commit logs to remote storage.\n");
    }

    @Option(name = "--cl-archive", usage = "Override path to the commit log archive directory, relative to the container root.")
    @Nullable
    public Path commitLogArchiveOverride;
}
