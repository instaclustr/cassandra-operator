package com.instaclustr.cassandra.backup.local;

import static com.instaclustr.cassandra.backup.guice.BackupRestoreBindings.installBindings;

import com.google.inject.AbstractModule;

public class LocalFileModule extends AbstractModule {
    @Override
    protected void configure() {
        installBindings(binder(),
                        "file",
                        LocalFileRestorer.class,
                        LocalFileBackuper.class);
    }
}
