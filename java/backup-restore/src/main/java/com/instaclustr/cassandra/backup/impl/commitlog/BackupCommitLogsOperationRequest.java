package com.instaclustr.cassandra.backup.impl.commitlog;

import java.nio.file.Path;

import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import picocli.CommandLine;

public class BackupCommitLogsOperationRequest extends BackupOperationRequest {

    @CommandLine.Option(
            names = {"--cl-archive"},
            description = "Override path to the commit log archive directory, relative to the container root."
    )
    public Path commitLogArchiveOverride;

    public BackupCommitLogsOperationRequest() {
        // for picocli
    }
}
