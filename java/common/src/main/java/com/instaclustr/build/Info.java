package com.instaclustr.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Info {
    static final Logger logger = LoggerFactory.getLogger(Info.class);

    private static Properties properties = new Properties() {{
        try {
            load(Info.class.getClassLoader().getResourceAsStream("git.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }};


    public static String readGitProperties(String property) throws IOException {
        return properties.getProperty(property);
    }

    public static void logVersionInfo() {
        try {
            logger.info("Version built from upstream repo [{}] on branch [{}] using commit: [{}]", Info.readGitProperties("git.remote.origin.url"), Info.readGitProperties("git.branch"), Info.readGitProperties("git.commit.id.describe"));
        } catch (IOException e) {
            logger.warn("Could not determine build version");
        }

    }
}
