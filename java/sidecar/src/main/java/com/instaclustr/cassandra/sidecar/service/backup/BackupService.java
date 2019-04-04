package com.instaclustr.cassandra.sidecar.service.backup;

import com.google.common.util.concurrent.AbstractIdleService;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.util.GlobalLock;
import com.microsoft.azure.storage.StorageException;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupService extends AbstractIdleService {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void enqueueBackup(final BackupArguments arguments) throws IOException, StorageException, ConfigurationException, URISyntaxException {
        final BackupTask backupTask = new BackupTask(arguments, new GlobalLock("/tmp"));

        executorService.submit(backupTask);
    }

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
        // TODO: gracefully stop any running backups
    }
}
