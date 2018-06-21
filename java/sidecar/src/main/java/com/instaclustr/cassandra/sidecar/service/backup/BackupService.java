package com.instaclustr.cassandra.sidecar.service.backup;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.util.GlobalLock;
import com.microsoft.azure.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BackupService extends AbstractExecutionThreadService {
    private final BlockingQueue<BackupTask> backupTaskQueue = new LinkedBlockingQueue<>(5); //Can have one backup task at any given time
    private BackupTask activeTask = null;
    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);


    public boolean enqueueBackup(BackupArguments arguments) throws IOException, StorageException, ConfigurationException, URISyntaxException {
        final BackupTask backupTask = new BackupTask(arguments, new GlobalLock("/tmp"));
        return backupTaskQueue.offer(backupTask);
    }

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void run() throws Exception {
        while(isRunning()) {
            BackupTask task = backupTaskQueue.take();
            try {
                task.call();
            } catch (Exception e) {
                logger.error("Could not run backup task", e);
            }
        }
    }

    @Override
    protected void shutDown() throws Exception {
        if(activeTask!=null)
            activeTask.stopBackupTask();
    }
}
