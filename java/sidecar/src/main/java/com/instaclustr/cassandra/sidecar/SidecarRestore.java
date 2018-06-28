package com.instaclustr.cassandra.sidecar;

import com.instaclustr.backup.RestoreApplication;
import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.task.RestoreTask;
import com.instaclustr.backup.util.GlobalLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class SidecarRestore {
    private static final Logger logger = LoggerFactory.getLogger(RestoreApplication.class);

    public static void main(final String[] args) throws IOException {

        final RestoreArguments arguments = new RestoreArguments("cassandra-restore", System.err);
        arguments.parseArguments(args);

        String name = Files.readAllLines(Paths.get("/etc/podinfo/name")).stream().collect(Collectors.joining());

        try {
            GlobalLock globalLock = new GlobalLock("/tmp");
            arguments.sourceNodeID = arguments.sourceNodeID + name.split("-")[2]; //make getting the ordinal more robust
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