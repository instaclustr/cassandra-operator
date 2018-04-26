package com.instaclustr.backup.uploader;

import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.base.Optional;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.StorageProvider;
import com.microsoft.azure.storage.StorageException;

import javax.naming.ConfigurationException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

public class SnapshotUploaderFactory {
    public static SnapshotUploader get(final BackupArguments arguments) throws URISyntaxException, StorageException, ConfigurationException {
        //final String backupID, final String clusterID, final String backupBucket,

        switch (arguments.storageProvider) {
            case AWS_S3:
                //TODO: support encrypted backups via KMS
                //AWS client set to auto detect credentials
                return new AWSSnapshotUploader(arguments.snapshotTag, arguments.clusterID, arguments.backupBucket, Optional.absent());
            case AZURE_BLOB:
                //TODO: use SAS token?
                return new AzureSnapshotUploader(arguments.snapshotTag, arguments.clusterID, arguments.backupBucket, arguments.account, arguments.secret);
            case GCP_BLOB:
                return new GCPSnapshotUploader(arguments.snapshotTag, arguments.clusterID, arguments.backupBucket);
            case FILE:
                return new LocalFileSnapShotUploader(Paths.get(arguments.backupBucket)); //TODO: fix doco
        }
        throw new ConfigurationException("Could not create Snapshot Uploader");
    }
}
