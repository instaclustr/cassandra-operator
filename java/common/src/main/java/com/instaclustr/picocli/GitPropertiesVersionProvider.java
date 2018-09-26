package com.instaclustr.picocli;

import com.instaclustr.build.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;

public class GitPropertiesVersionProvider implements CommandLine.IVersionProvider {
    static final Logger logger = LoggerFactory.getLogger(GitPropertiesVersionProvider.class);


    @Override
    public String[] getVersion() {
        String version = "Unknown version";
        try {
            version = Info.getInfoString();
        } catch (IOException e) {
            logger.warn("Could not detect version", e);
        }
        return new String[]{version};
    }
}
