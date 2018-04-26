//package com.instaclustr.backup;
//
//import com.google.common.collect.ImmutableList;
//import com.google.inject.assistedinject.FactoryModuleBuilder;
//import com.instaclustr.events.EventsModule;
//import com.instaclustr.nodeagent.EventsSupportModule;
//import com.instaclustr.nodeagent.cassandra.backup.task.CommitLogBackupTaskFactory;
//import com.instaclustr.nodeagent.cassandra.backup.util.ProviderInjection;
//import com.instaclustr.nodeagent.common.NodeAgentModule;
//import com.instaclustr.nodeagent.common.rmq.RMQSupportModule;
//import com.instaclustr.rabbitmq.RabbitMQModule;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Random;
//import java.util.concurrent.TimeUnit;
//
//public class CommitLogBackupApplication extends Application {
//    private static final Logger logger = LoggerFactory.getLogger(CommitLogBackupApplication.class);
//
//    public static void main(String[] args) {
//        final CommitLogBackupArguments arguments = new CommitLogBackupArguments("commitlog-com.instaclustr.backup", System.err);
//        arguments.parseArguments(args);
//
//        final ImmutableList.Builder<Module> moduleListBuilder = ImmutableList.builder();
//        // common modules
//        moduleListBuilder.add(
//                new AbstractModule() {
//                    @Override
//                    protected void configure() {
//                        bind(CommitLogBackupArguments.class).toInstance(arguments);
//                        bind(CommonBackupArguments.class).toInstance(arguments);
//                        bind(BaseArguments.class).toInstance(arguments);
//                        install(new FactoryModuleBuilder().build(CommitLogBackupTaskFactory.class));
//                    }
//                },
//                new EventsSupportModule(),
//                new EventsModule(),
//                new RabbitMQModule(),
//                new RMQSupportModule(),
//                new NodeAgentModule()
//        ).addAll(ProviderInjection.providerSpecificModules(arguments));
//
//        {
//            final Injector injector;
//
//            try {
//                injector = createInjector(moduleListBuilder.build());
//                injector.getInstance(CommitLogBackupTaskFactory.class).commitLogBackupTask().call();
//                logger.info("CommitLog Backup completed successfully.");
//                System.exit(0);
//            } catch (final java.net.SocketTimeoutException e){
//                logger.error("Timeout Error  - Assumed due to RMQ timeout. Exiting in 10 seconds");
//
////                Wait is random to reduce simultaneous retry load onRabbitMQ (all nodes try 1st time at 5 minutes)
//                Random rn = new Random();
//                try {
//                    Thread.sleep(TimeUnit.SECONDS.toMillis(rn.nextInt(10)));
//                } catch (InterruptedException e1) {
//                }
//                logger.error("Failed to complete CommitLog com.instaclustr.backup.", e);
//            } catch (final Exception e) {
//                logger.error("Failed to complete CommitLog com.instaclustr.backup.", e);
//            }
//            System.exit(1);
//        }
//    }
//}
