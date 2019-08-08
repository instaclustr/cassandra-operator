package com.instaclustr.cassandra.backup.guice;

import com.instaclustr.cassandra.backup.impl.backup.BackupCommitLogsOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;

public interface BackuperFactory<BACKUPER extends Backuper> {
    BACKUPER createBackuper(final BackupOperationRequest backupOperationRequest);
    BACKUPER createCommitLogBackuper(final BackupCommitLogsOperationRequest backupCommitLogsOperationRequest);
}
