package com.instaclustr.cassandra.backup.aws;

import static com.instaclustr.cassandra.backup.guice.BackupRestoreBindings.installBindings;

import com.amazonaws.SdkClientException;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.throwingproviders.CheckedProvider;
import com.google.inject.throwingproviders.CheckedProvides;
import com.google.inject.throwingproviders.ThrowingProviderBinder;

public class S3Module extends AbstractModule {
    @Override
    protected void configure() {
        install(ThrowingProviderBinder.forModule(this));
        installBindings(binder(),
                        "s3",
                        S3Restorer.class,
                        S3Backuper.class);
    }

    public interface TransferManagerProvider extends CheckedProvider<TransferManager> {
        TransferManager get() throws SdkClientException;
    }

    @CheckedProvides(TransferManagerProvider.class)
    TransferManager provideTransferManager(final AmazonS3 amazonS3) {
        /*
         * Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (RECOMMENDED since they are recognized by all the AWS SDKs and CLI except for .NET), or AWS_ACCESS_KEY and AWS_SECRET_KEY (only recognized by Java SDK)
         * Java System Properties - aws.accessKeyId and aws.secretKey
         * Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
         * Credentials delivered through the Amazon EC2 container service if AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" environment variable is set and security manager has permission to access the variable,
         * Instance profile credentials delivered through the Amazon EC2 metadata service
         *
         */

        return TransferManagerBuilder.standard().withS3Client(amazonS3).build();
    }

    @Provides
    AmazonS3 provideAmazonS3() {
        final AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        final String envAWSRegion = System.getenv("AWS_REGION");
        final String envAWSEndpoint = System.getenv("AWS_ENDPOINT");

        if (envAWSEndpoint != null) {
            // AWS_REGION must be set if AWS_ENDPOINT is set
            if (envAWSRegion == null) {
                throw new IllegalArgumentException("AWS_REGION must be set if AWS_ENDPOINT is set.");
            }

            return builder.withEndpointConfiguration(
                new EndpointConfiguration(envAWSEndpoint, envAWSRegion.toLowerCase())).build();
        }
        else if (envAWSRegion != null) {
            return builder.withRegion(Regions.fromName(envAWSRegion.toLowerCase())).build();
        } else {
            return builder.build();
        }
    }
}
