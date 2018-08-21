package com.instaclustr.cassandra.sidecar;

import com.instaclustr.backup.RestoreArguments;
import com.instaclustr.backup.task.RestoreTask;
import com.instaclustr.backup.util.GlobalLock;
import com.instaclustr.build.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SidecarRestore {
    private static final Logger logger = LoggerFactory.getLogger(SidecarRestore.class);

    public static void main(final String[] args) throws IOException {
        Info.logVersionInfo();


        final RestoreArguments arguments = new RestoreArguments("cassandra-restore", System.err);
        arguments.parseArguments(args);

        Pattern p = Pattern.compile("(\\d+$)");
        Matcher m = p.matcher(Files.readAllLines(Paths.get("/etc/podinfo/name")).stream().collect(Collectors.joining()));
        m.find();
        String ordinal = m.group();


        logger.info("detected ordinal is {}", ordinal);

        try {
            GlobalLock globalLock = new GlobalLock("/tmp");
            arguments.sourceNodeID = arguments.sourceNodeID  + "-"  + ordinal; //make getting the ordinal more robust
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