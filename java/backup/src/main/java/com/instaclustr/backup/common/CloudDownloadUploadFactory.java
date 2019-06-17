package com.instaclustr.backup.common;

import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.instaclustr.backup.model.BackupArguments;
import com.instaclustr.backup.model.RestoreArguments;
import com.instaclustr.backup.downloader.*;
import com.instaclustr.backup.uploader.*;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;

import javax.naming.ConfigurationException;
import java.net.URISyntaxException;

public class CloudDownloadUploadFactory {

    public static TransferManager getTransferManager() {
        /*
         * Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (RECOMMENDED since they are recognized by all the AWS SDKs and CLI except for .NET), or AWS_ACCESS_KEY and AWS_SECRET_KEY (only recognized by Java SDK)
         * Java System Properties - aws.accessKeyId and aws.secretKey
         * Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
         * Credentials delivered through the Amazon EC2 container service if AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" environment variable is set and security manager has permission to access the variable,
         * Instance profile credentials delivered through the Amazon EC2 metadata service
         *
         */

        return TransferManagerBuilder.defaultTransferManager();
    }

    public static CloudBlobClient getCloudBlobClient() {
        //TODO: Implement!
        return null;
    }

    public static Storage getGCPStorageClient() {
        /*
         * Instance profile,
         * GOOGLE_APPLICATION_CREDENTIALS env var, or
         * application_default_credentials.json default
         */
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
