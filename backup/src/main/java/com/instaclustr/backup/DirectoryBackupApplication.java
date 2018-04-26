//package com.instaclustr.backup;
//
//import com.google.common.collect.ImmutableList;
//import com.google.inject.assistedinject.FactoryModuleBuilder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.nio.file.Paths;
//import java.time.ZonedDateTime;
//import java.time.format.DateTimeFormatter;
//
//public class DirectoryBackupApplication extends Application {
//    private static final Logger logger = LoggerFactory.getLogger(DirectoryBackupApplication.class);
//
//    public static void main(String[] args) throws Exception {
//        final DirectoryBackupArguments arguments = new DirectoryBackupArguments("directory-com.instaclustr.backup", System.err);
//        arguments.parseArguments(args);
//
//        final ImmutableList.Builder<Module> moduleListBuilder = ImmutableList.builder();
//        // Common modules
//        moduleListBuilder.add(
//                new AbstractModule() {
//                    @Override
//                    protected void configure() {
//                        bind(DirectoryBackupArguments.class).toInstance(arguments);
//                        bind(CommonBackupArguments.class).toInstance(arguments);
//                        bind(BaseArguments.class).toInstance(arguments);
//                        install(new FactoryModuleBuilder().build(DirectoryBackupFactory.class));
//                    }
//                },
//                new EventsSupportModule(),
//                new EventsModule(),
//                new RabbitMQModule(),
//                new RMQSupportModule(),
//                new NodeAgentModule()
//        ).addAll(ProviderInjection.providerSpecificModules(arguments));
//
//        // Main Service Injection
//        {
//            final Injector injector = createInjector(moduleListBuilder.build());
//
//            try {
//                injector.getInstance(DirectoryBackupFactory.class).backupTask(Paths.get(arguments.rootLabel, ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)).toString()).call();
//                logger.info("Backup completed successfully.");
//                System.exit(0);
//
//            } catch (final Exception e) {
//                logger.error("Failed to complete com.instaclustr.backup.", e);
//                System.exit(1);
//            }
//        }
//    }
//}
