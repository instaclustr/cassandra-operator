package com.instaclustr.cassandra.sidecar.picocli;

import com.instaclustr.picocli.JarManifestVersionProvider;

public class SidecarJarManifestVersionProvider extends JarManifestVersionProvider {
    protected SidecarJarManifestVersionProvider() {
        super("cassandra-sidecar");
    }
}
