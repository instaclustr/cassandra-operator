package com.instaclustr.cassandra.backup.gcp;

import static com.instaclustr.cassandra.backup.guice.BackupRestoreBindings.installBindings;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.AbstractModule;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProvides;
import com.google.inject.throwingproviders.ThrowingProviderBinder;

public class GCPModule extends AbstractModule {
    @Override
    protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
        installBindings(binder(),
                        "gcp",
                        GCPRestorer.class,
                        GCPBackuper.class);
    }

    public interface StorageProvider extends CheckedProvider<Storage> {
        Storage get() throws RuntimeException;
    }

    @CheckedProvides(StorageProvider.class)
    Storage provideStorage() {
        /*
         * Instance profile,
         * GOOGLE_APPLICATION_CREDENTIALS env var, or
         * application_default_credentials.json default
         */
        return StorageOptions.getDefaultInstance().getService();
    }
}
