package com.instaclustr.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    public static void cleanDirectory(final File directory) {

        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] fileList = directory.listFiles();

        if (fileList == null) {
            return;
        }

        for (File file : fileList) {
            if (file.isDirectory()) cleanDirectory(file);

            if (!file.delete()) {
                logger.warn("Failed to delete {}", file.getAbsolutePath());
            }
        }
    }

    public static void cleanDirectory(final Path path) {
        cleanDirectory(path.toFile());
    }

    public static void replaceInFile(final Path file,
                                     final String toReplace, final String replacement) throws IOException {

        final String contents = new String(Files.readAllBytes(file));

        Files.write(file, ImmutableList.of(contents.replace(toReplace, replacement)), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    public static void appendToFile(final Path file,
                                    final String content) throws IOException {

        final String contents = new String(Files.readAllBytes(file));

        Files.write(file, ImmutableList.of(content), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public static void appendToFile(final Path file,
                                    final Path fileToAppend) throws IOException {
        appendToFile(file, new String(Files.readAllBytes(fileToAppend)));
    }
}
