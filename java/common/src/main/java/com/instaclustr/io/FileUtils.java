package com.instaclustr.io;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    public static void cleanDirectory(final File directory) {
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
}
