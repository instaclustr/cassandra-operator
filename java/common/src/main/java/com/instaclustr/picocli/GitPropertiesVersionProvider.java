package com.instaclustr.picocli;

import com.instaclustr.build.Info;
import picocli.CommandLine;

import java.io.IOException;

public class GitPropertiesVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        String version = "Unknown version";
        try {
            version = Info.getInfoString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String[]{version}; // TODO: load from MANIFEST.MF (and insert build/commit details into manifest on mvn pkg)
    }
}
