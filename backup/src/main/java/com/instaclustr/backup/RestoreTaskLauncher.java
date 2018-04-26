//package com.instaclustr.backup;
//
//import com.google.common.collect.HashMultimap;
//import com.google.common.collect.ImmutableList;
//import com.google.common.collect.Multimap;
//import com.google.inject.assistedinject.FactoryModuleBuilder;
//import com.google.inject.multibindings.OptionalBinder;
//import com.google.inject.name.Names;
//import com.instaclustr.aws.AWSCredentialsModule;
//import com.instaclustr.azure.AzureStorageClientModule;
//import com.instaclustr.common.configuration.ConfigurationModule;
//import com.instaclustr.common.domain.ClusterDataCentrePrimaryKey;
//import com.instaclustr.common.domain.ClusterPrimaryKey;
//import com.instaclustr.events.EventsModule;
//import com.instaclustr.nodeagent.ConfigurationProperties;
//import com.instaclustr.nodeagent.EventsSupportModule;
//import com.instaclustr.nodeagent.cassandra.backup.downloader.AWSDownloaderFactory;
//import com.instaclustr.nodeagent.cassandra.backup.downloader.AzureDownloaderFactory;
//import com.instaclustr.nodeagent.cassandra.backup.downloader.Downloader;
//import com.instaclustr.nodeagent.cassandra.backup.downloader.GCPDownloaderFactory;
//import com.instaclustr.nodeagent.cassandra.backup.modules.AWSSupportModule;
//import com.instaclustr.nodeagent.cassandra.backup.modules.GCPSupportModule;
//import com.instaclustr.nodeagent.cassandra.backup.modules.RestoreNodesModule;
//import com.instaclustr.nodeagent.cassandra.backup.task.RestoreTask;
//import com.instaclustr.nodeagent.cassandra.backup.uploader.AzureSnapshotUploader;
//import com.instaclustr.nodeagent.cassandra.backup.uploader.SnapshotUploader;
//import com.instaclustr.nodeagent.common.NodeAgentModule;
//import com.instaclustr.nodeagent.common.rmq.RMQSupportModule;
//import com.instaclustr.nodeagent.thrift.NodeAgentException;
//import com.instaclustr.rabbitmq.RabbitMQModule;
//import com.instaclustr.systemd.SystemdModule;
//import org.apache.commons.lang3.StringUtils;
//import org.freedesktop.dbus.DBusModule;
//
//public class RestoreTaskLauncher {
//    public static void run(final RestoreArguments restoreArguments) throws Exception {
//
//        Multimap<String, String> keyspaceTableSubset = HashMultimap.create();
//
//        if (restoreArguments.keyspaceTables != null) {
//            for (String rawPair : restoreArguments.keyspaceTables.split(",")) {
//                String[] pair = StringUtils.split(rawPair.trim(), '.');
//                if (pair.length != 2)
//                    throw new NodeAgentException().setMessage("Keyspace-tables requires a comma-separate list of keyspace-table pairs, e.g., 'test.test1,test2.test1'");
//
//                keyspaceTableSubset.put(pair[0], pair[1]);
//            }
//        }
//
//        final ImmutableList.Builder<Module> moduleListBuilder = ImmutableList.builder();
//
//        moduleListBuilder.add(
//                new AbstractModule() {
//                    @Override
//                    protected void configure() {
//                        OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, ConfigurationProperties.named(ConfigurationProperties.CASSANDRA_COMMIT_LOG_BACKUP_DIRECTORY)));
//                        bind(RestoreArguments.class).toInstance(restoreArguments);
//                        bind(BaseArguments.class).toInstance(restoreArguments);
//                        bind(new TypeLiteral<Multimap<String, String>>(){}).toInstance(keyspaceTableSubset);
//                    }
//                },
//                new DBusModule(),
//                new SystemdModule(),
//                new EventsSupportModule(),
//                new EventsModule(),
//                new RabbitMQModule(),
//                new RMQSupportModule(),
//                new NodeAgentModule(),
//                new RestoreNodesModule(),
//                new ConfigurationModule()
//        );
//
//        {
//            final Injector configurationInjector = Guice.createInjector(new ConfigurationModule());
//            // TODO: Provider should be based on the source cluster
//            final com.instaclustr.common.domain.Provider provider = configurationInjector.getInstance(Key.get(com.instaclustr.common.domain.Provider.class, ConfigurationProperties.named(ConfigurationProperties.NODE_PROVIDER)));
//
//            switch (provider) {
//                case AWS_VPC:
//                case VAGRANT:
//                case SOFTLAYER_BARE_METAL:
//                    moduleListBuilder.add(new AbstractModule() {
//                        @Override
//                        protected void configure() {
//                            install(new FactoryModuleBuilder().build(AWSDownloaderFactory.class));
//                        }
//
//                        @Provides
//                        Downloader provide(AWSDownloaderFactory factory) {
//                            return factory.awsDownloader(new ClusterPrimaryKey(restoreArguments.clusterId), restoreArguments.sourceBackupID, restoreArguments.backupBucket);
//                        }
//                    }, new AWSSupportModule());
//                    moduleListBuilder.add(new AWSCredentialsModule());
//                    break;
//
//                case AZURE:
//                    moduleListBuilder.add(new AbstractModule() {
//                                              @Override
//                                              protected void configure() {
//                                                  bind(SnapshotUploader.class).to(AzureSnapshotUploader.class);
//                                                  install(new FactoryModuleBuilder().build(AzureDownloaderFactory.class));
//                                              }
//
//                                              @Provides
//                                              Downloader provide(AzureDownloaderFactory factory) {
//                                                  return factory.azureDownloader(new ClusterPrimaryKey(restoreArguments.clusterId), restoreArguments.sourceBackupID);
//                                              }
//                                          },
//                            new AbstractModule() {
//                                @Override
//                                protected void configure() {
//                                    OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, Names.named("RestoreSasToken"))).setBinding().toInstance(restoreArguments.azureSasToken);
//                                }
//                            },
//                            new AzureStorageClientModule()
//                    );
//                    break;
//
//                case GCP:
//                    moduleListBuilder.add(new AbstractModule() {
//                        @Override
//                        protected void configure() {
//                            install(new FactoryModuleBuilder().build(GCPDownloaderFactory.class));
//                        }
//
//                        @Provides
//                        Downloader provide(GCPDownloaderFactory factory) {
//                            return factory.gcpDownloader(new ClusterDataCentrePrimaryKey(restoreArguments.clusterDataCentreId), restoreArguments.sourceBackupID);
//                        }
//                    }, new GCPSupportModule());
//                    break;
//            }
//        }
//
//        {
//            final Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleListBuilder.build());
//            injector.getInstance(RestoreTask.class).call();
//        }
//    }
//}
