package com.instaclustr.picocli;

import picocli.CommandLine;

public class ManifestVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[]{"Version information not available"}; // TODO: load from MANIFEST.MF (and insert build/commit details into manifest on mvn pkg)
    }
}
