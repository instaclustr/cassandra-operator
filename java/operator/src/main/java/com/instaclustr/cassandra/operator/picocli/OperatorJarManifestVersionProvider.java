package com.instaclustr.cassandra.operator.picocli;

import com.instaclustr.picocli.JarManifestVersionProvider;

public class OperatorJarManifestVersionProvider extends JarManifestVersionProvider {
    protected OperatorJarManifestVersionProvider() {
        super("cassandra-operator");
    }
}
