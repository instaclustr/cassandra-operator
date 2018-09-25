package com.instaclustr.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Info {
    static final Logger logger = LoggerFactory.getLogger(Info.class);

    private final static Properties properties = new Properties() {{
        try {
            load(Info.class.getClassLoader().getResourceAsStream("git.properties"));
        } catch (IOException e) {
            logger.warn("Could not load git.properties");
        }
    }};

    public static String getInfoString() throws IOException {
        return String.format("Version built from upstream repo [%s] on branch [%s] using commit: [%s]", Info.readGitProperties("git.remote.origin.url"), Info.readGitProperties("git.branch"), Info.readGitProperties("git.commit.id.describe"));
    }


    private static String readGitProperties(String property) throws IOException {
        return properties.getProperty(property);
    }

    public static void logVersionInfo() {
        try {
            logger.info(getInfoString());
        } catch (IOException e) {
            logger.warn("Could not determine build version");
        }

    }
}
