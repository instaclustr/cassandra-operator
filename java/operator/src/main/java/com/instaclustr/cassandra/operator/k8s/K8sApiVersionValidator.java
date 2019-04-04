package com.instaclustr.cassandra.operator.k8s;

import io.kubernetes.client.apis.VersionApi;
import io.kubernetes.client.models.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class K8sApiVersionValidator implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(K8sApiVersionValidator.class);

    public static class Options {
        @CommandLine.Option(names = "--no-api-version-check", description = "Skip")
        boolean noVersionCheck;
    }

    private final VersionApi versionApi;
    private final Options options;

    @Inject
    public K8sApiVersionValidator(final VersionApi versionApi, final Options options) {
        this.versionApi = versionApi;
        this.options = options;
    }

    public static class IncompatibleKubernetesApiVersionException extends IllegalStateException {}

    @Override
    public Void call() throws Exception {
        final VersionInfo versionInfo = versionApi.getCode();

        logger.info("Server Kubernetes API version: {}.{}", versionInfo.getMajor(), versionInfo.getMinor());

        if (options.noVersionCheck) {
            logger.warn("Skipping Kubernetes API version compatibility check.");
            return null;
        }

        final int major = Integer.parseInt(versionInfo.getMajor().replaceAll("\\D+", ""));
        final int minor = Integer.parseInt(versionInfo.getMinor().replaceAll("\\D+", ""));

        if (major < 1) {
            // waaaay to old
            throw new IncompatibleKubernetesApiVersionException();
        }

        if (major == 1 && minor < 5) {
            // StatefulSets were introduced in 1.5
            throw new IncompatibleKubernetesApiVersionException();
        }

        if ((major == 1 && minor > 11) || major > 1) {
            logger.warn("The cassandra-operator has not been validated to work on this version of the Kubernetes API.");
        }

        return null;
    }
}
