package com.instaclustr.cassandra.backup.azure;

import static com.instaclustr.cassandra.backup.guice.BackupRestoreBindings.installBindings;

import java.net.URISyntaxException;

import com.google.inject.AbstractModule;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProvides;
import com.google.inject.throwingproviders.ThrowingProviderBinder;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudBlobClient;

public class AzureModule extends AbstractModule {
    @Override
    protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
        installBindings(binder(),
                        "azure",
                        AzureRestorer.class,
                        AzureBackuper.class);
    }

    public interface CloudStorageAccountProvider extends CheckedProvider<CloudStorageAccount> {
        CloudStorageAccount get() throws URISyntaxException;
    }

    public interface CloudBlobClientProvider extends CheckedProvider<CloudBlobClient> {
        CloudBlobClient get() throws URISyntaxException, IllegalArgumentException;
    }

    @CheckedProvides(CloudStorageAccountProvider.class)
    CloudStorageAccount provideCloudStorageAccount() throws URISyntaxException {
        return new CloudStorageAccount(new StorageCredentialsAccountAndKey(System.getenv("AZURE_STORAGE_ACCOUNT"),
                                                                           System.getenv("AZURE_STORAGE_KEY")),
                                       true); // use https
    }

    @CheckedProvides(CloudBlobClientProvider.class)
    CloudBlobClient provideCloudBlobClient(final CloudStorageAccountProvider cloudStorageAccount) throws URISyntaxException {
        return cloudStorageAccount.get().createCloudBlobClient();
    }
}
