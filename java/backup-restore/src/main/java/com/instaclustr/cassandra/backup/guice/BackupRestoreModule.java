package com.instaclustr.cassandra.backup.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.cassandra.backup.aws.S3Module;
import com.instaclustr.cassandra.backup.azure.AzureModule;
import com.instaclustr.cassandra.backup.gcp.GCPModule;
import com.instaclustr.threading.Executors;
import com.instaclustr.cassandra.backup.impl.backup.BackupModule;
import com.instaclustr.cassandra.backup.impl.restore.RestoreModule;
import com.instaclustr.cassandra.backup.local.LocalFileModule;

public class BackupRestoreModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new S3Module());
        install(new AzureModule());
        install(new GCPModule());
        install(new LocalFileModule());
        install(new BackupModule());
        install(new RestoreModule());
    }

    @Provides
    @Singleton
    Executors.ExecutorServiceSupplier getFileUploaderExecutorSupplier() {
        return new Executors.FixedTasksExecutor();
    }
}
