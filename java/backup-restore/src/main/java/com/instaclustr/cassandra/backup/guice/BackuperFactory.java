package com.instaclustr.cassandra.backup.guice;

import com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequest;
import com.instaclustr.cassandra.backup.impl.backup.Backuper;

@FunctionalInterface
public interface BackuperFactory<BACKUPER extends Backuper> {
    BACKUPER createBackuper(final BackupOperationRequest backupOperationRequest);
}
