package com.instaclustr.cassandra.sidecar.service.backup;

import com.google.common.util.concurrent.AbstractIdleService;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.util.GlobalLock;
import com.microsoft.azure.storage.StorageException;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;

public class BackupService extends AbstractIdleService {

    public Backup startBackup() throws IOException, StorageException, ConfigurationException, URISyntaxException {
        final BackupArguments arguments = new BackupArguments("cassandra-backup", System.out);// TODO: refactor this args class into something that's CLI vs non-CLI.

        final GlobalLock lock = new GlobalLock("/");

        final BackupTask backupTask = new BackupTask(arguments, lock);

        return new Backup(backupTask);
    }

    @Override
    protected void startUp() throws Exception {}

    @Override
    protected void shutDown() throws Exception {}
}
