package com.instaclustr.cassandra.backup;

import com.instaclustr.cassandra.backup.model.BackupArguments;
import com.instaclustr.cassandra.backup.task.BackupTask;
import com.instaclustr.cassandra.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BackupApplication extends Application{
    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    public static void main(String[] args) throws Exception {
        final BackupArguments arguments = new BackupArguments("cassandra-backup", System.err);
        arguments.parseArguments(args);

        GlobalLock globalLock = new GlobalLock(arguments.sharedContainerPath.toString());

        new BackupTask(arguments, globalLock).call();
    }
}
