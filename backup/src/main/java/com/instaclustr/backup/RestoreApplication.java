//package com.instaclustr.backup;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//
//public class RestoreApplication extends Application {
//    private static final Logger logger = LoggerFactory.getLogger(RestoreApplication.class);
//
//    public static void main(String[] args) throws Exception {
//        final RestoreArguments arguments = new RestoreArguments("cassandra-restore", System.err);
//        arguments.parseArguments(args);
//
//        try {
//            RestoreTaskLauncher.run(arguments);
//            logger.info("Restore completed successfully.");
//            System.exit(0);
//        } catch (final Exception e) {
//            logger.error("Failed to complete restore.", e);
//            System.exit(1);
//        }
//    }
//}
