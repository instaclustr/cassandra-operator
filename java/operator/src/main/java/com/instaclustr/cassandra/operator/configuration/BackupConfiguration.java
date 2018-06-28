package com.instaclustr.cassandra.operator.configuration;

import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.StorageProvider;

import java.nio.file.Paths;

public class BackupConfiguration {
    public static BackupArguments generateBackupArguments(final String ip, final int port, final String tag, final StorageProvider provider, final String target, final String cluster) {
        BackupArguments backupArguments = new BackupArguments();
        backupArguments.setJmxServiceURLFromIp(ip, port);
        backupArguments.cassandraConfigDirectory = Paths.get("/etc/cassandra/");
        backupArguments.cassandraDirectory = Paths.get("/var/lib/cassandra/");
        backupArguments.sharedContainerPath = Paths.get("/");
        backupArguments.snapshotTag = tag;
        backupArguments.storageProvider = provider;
        backupArguments.backupBucket = target;
        backupArguments.offlineSnapshot = false;
        backupArguments.account = "";
        backupArguments.secret = "";
        backupArguments.clusterId = cluster;
        return backupArguments;
    }

    public static RestoreArguments generateRestoreArguments() {
        RestoreArguments restoreArguments = new RestoreArguments();
        return restoreArguments;
    }
}


//'account': None, //Not used atm
//'backupBucket': None,
//'backupId': None,
//'bandwidth': None,
//'cassandraConfigDirectory': None,
//'cassandraDirectory': None,
//'clusterId': None,
//'columnFamily': None,
//'concurrentConnections': 10,
//'drain': False,
//'duration': None,
//'fileBackupDirectory': None,
//'jmxServiceURL': None,
//'keyspaces': [],
//'offlineSnapshot': None,
//'secret': None, //Not used atm
//'sharedContainerPath': None,
//'showHelp': False,
//'snapshotTag': None,
//'speed': None,
//'storageProvider': None,
//'waitForLock': False