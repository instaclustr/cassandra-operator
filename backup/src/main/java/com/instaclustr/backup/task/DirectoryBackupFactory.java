package com.instaclustr.backup.task;

public interface DirectoryBackupFactory {
    DirectoryBackupTask backupTask(final String rootLabel);
}
