package com.instaclustr.backup;

import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.uploader.FilesUploader;
import com.instaclustr.backup.uploader.SnapshotUploaderFactory;
import com.instaclustr.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BackupApplication {
    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    public static void main(String[] args) throws Exception {
        final BackupArguments arguments = new BackupArguments("cassandra-com.instaclustr.backup", System.err);
        arguments.parseArguments(args);

        GlobalLock globalLock = new GlobalLock(arguments.sharedContainerPath.toString());

        BackupTask task = new BackupTask(new FilesUploader(SnapshotUploaderFactory.get(arguments), arguments), arguments, globalLock);

        task.call();

    }
}
