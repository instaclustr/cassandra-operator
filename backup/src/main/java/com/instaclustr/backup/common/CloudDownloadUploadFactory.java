package com.instaclustr.backup.common;

import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Optional;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.CommonBackupArguments;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.downloader.*;
import com.instaclustr.backup.uploader.*;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;

import javax.naming.ConfigurationException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CloudDownloadUploadFactory {

    public static TransferManager getTransferManager() {
        return TransferManagerBuilder.defaultTransferManager();
    }

    public static CloudBlobClient getCloudBlobClient() {
        return null;
    }

    public static Storage getGCPStorageClient() {
        return StorageOptions.getDefaultInstance().getService();
    }



    public static SnapshotUploader getUploader(final BackupArguments arguments) throws URISyntaxException, StorageException, ConfigurationException {
        //final String backupID, final String clusterID, final String backupBucket,

        switch (arguments.storageProvider) {
            case AWS_S3:
                //TODO: support encrypted backups via KMS
                //AWS client set to auto detect credentials
                return new AWSSnapshotUploader(getTransferManager(), arguments);
            case AZURE_BLOB:
                //TODO: use SAS token?
                return new AzureSnapshotUploader(getCloudBlobClient(), arguments);
            case GCP_BLOB:
                return new GCPSnapshotUploader(getGCPStorageClient(), arguments);
            case FILE:
                return new LocalFileSnapShotUploader(arguments);
        }
        throw new ConfigurationException("Could not create Snapshot Uploader");
    }


    public static Downloader getDownloader(final RestoreArguments arguments) throws URISyntaxException, StorageException, ConfigurationException {
        switch (arguments.storageProvider) {
            case AWS_S3:
                //TODO: support encrypted backups via KMS
                //AWS client set to auto detect credentials
                return new AWSDownloader(getTransferManager(), arguments);
            case AZURE_BLOB:
                //TODO: use SAS token?
                return new AzureDownloader(getCloudBlobClient(), arguments);
            case GCP_BLOB:
                return new GCPDownloader(getGCPStorageClient(), arguments);
            case FILE:
                return new LocalFileDownloader(arguments);
        }
        throw new ConfigurationException("Could not create Snapshot Uploader");
    }


}
