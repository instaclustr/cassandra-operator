package com.instaclustr.picocli;

import javax.validation.ValidationException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

import picocli.CommandLine;

public abstract class CLIApplication extends JarManifestVersionProvider {

    public static int execute(final Runnable runnable, String... args) {
        return execute(new CommandLine(runnable), args);
    }

    public static int execute(final Callable callable, String... args) {
        return execute(new CommandLine(callable), args);
    }

    public static int execute(CommandLine commandLine, String... args) {
        return commandLine
                .setErr(new PrintWriter(System.err))
                .setOut(new PrintWriter(System.err))
                .setColorScheme(new CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.ON).build())
                .setExecutionExceptionHandler((ex, cmdLine, parseResult) -> {

                    if (ex instanceof ValidationException) {
                        return 1;
                    }

                    ex.printStackTrace();

                    return 1;
                })
                .execute(args);
    }
}
