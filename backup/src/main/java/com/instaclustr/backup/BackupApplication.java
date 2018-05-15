package com.instaclustr.backup;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ServiceManager;
import com.instaclustr.backup.task.BackupTask;
import com.instaclustr.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public class BackupApplication extends Application{
    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    public static void main(String[] args) throws Exception {
        final BackupArguments arguments = new BackupArguments("cassandra-backup", System.err);
        arguments.parseArguments(args);

        GlobalLock globalLock = new GlobalLock(arguments.sharedContainerPath.toString());

        new BackupTask(arguments, globalLock).call();
    }
}
