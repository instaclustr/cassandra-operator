package com.instaclustr.cassandra.backup;

import com.instaclustr.cassandra.backup.model.RestoreArguments;
import com.instaclustr.cassandra.backup.task.RestoreTask;
import com.instaclustr.cassandra.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RestoreApplication {
    private static final Logger logger = LoggerFactory.getLogger(RestoreApplication.class);

    public static void main(String[] args) throws Exception {
        final RestoreArguments arguments = new RestoreArguments("cassandra-restore", System.err);
        arguments.parseArguments(args);

        try {
            GlobalLock globalLock = new GlobalLock(arguments.sharedContainerPath.toString());
            new RestoreTask(
                    globalLock,
                    arguments
            ).call();
            logger.info("Restore completed successfully.");
            System.exit(0);
        } catch (final Exception e) {
            logger.error("Failed to complete restore.", e);
            System.exit(1);
        }
    }
}
