package com.instaclustr.backup;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

import java.io.PrintStream;

public abstract class BaseArguments {
    final String appName;
    final PrintStream stream;
    CmdLineParser parser;
    public String account;
    public String secret;

    BaseArguments(final String appName, final PrintStream stream) {
        this.appName = appName;
        this.stream = stream;
    }

    void parseArguments(String[] args) {
        try {
            this.parser = new CmdLineParser(this, ParserProperties.defaults().withUsageWidth(120).withOptionSorter(null));
            this.parser.parseArgument(args);

        } catch (final CmdLineException e) {
            printError(e.getLocalizedMessage());
        }

        if (this.showHelp) {
            printUsage();
            printHelp();
            System.exit(1);
        }
    }

    void printError(String message) {
        stream.format("ERROR %s: ", appName);
        stream.println(message);
        stream.println();
        printUsage();
        stream.format("For detailed help run `%s --help`.%n", appName);
        System.exit(1);
    }

    private void printUsage() {
        stream.print("Usage: ");
        stream.print(appName);
        parser.printSingleLineUsage(stream);
        stream.println();
        parser.printUsage(stream);
        stream.println();
    }

    abstract void printHelp();

    @Option(name = "--concurrent-connections", usage = "Number of files (or file parts) to upload or download concurrently. Higher values will increase throughput. Default is 10.", metaVar = "count")
    public Integer concurrentConnections = 10;

    @Option(name="--wait", usage = "Wait to acquire the global transfer lock (which prevents more than one com.instaclustr.backup or restore from running).")
    public boolean waitForLock;

    @Option(name="--help", usage = "Show this message.", help = true)
    public boolean showHelp;
}
