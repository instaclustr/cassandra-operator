package com.instaclustr.cassandra.backup.model;

import java.io.PrintStream;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

public class DirectoryBackupArguments extends CommonBackupArguments {
    public DirectoryBackupArguments(final String appName, final PrintStream stream) {
        super(appName, stream);
    }

    @Override
    void commonBackupPrintHelp() {
        stream.println("Take a com.instaclustr.backup of directories provided as arguments. This will be uploaded to remote storage");
    }

    @Argument(index = 0, metaVar = "rootLabel", usage = "Name of the com.instaclustr.backup root Directory", required = true)
    public String rootLabel;

    @Argument(index = 1, metaVar = "directory", usage = "List of source directories to com.instaclustr.backup", handler = StringArrayOptionHandler.class, required = true)
    public List<String> sources;
}
