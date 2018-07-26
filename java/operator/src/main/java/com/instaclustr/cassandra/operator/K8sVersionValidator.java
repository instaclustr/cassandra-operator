package com.instaclustr.cassandra.operator;

import io.kubernetes.client.apis.VersionApi;
import io.kubernetes.client.models.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public class K8sVersionValidator implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(K8sVersionValidator.class);

    static class Options {
        @CommandLine.Option(names = "--no-version-check")
        boolean noVersionCheck;
    }

    private final VersionApi versionApi;
    private final Options options;

    @Inject
    public K8sVersionValidator(final VersionApi versionApi, final Options options) {
        this.versionApi = versionApi;
        this.options = options;
    }

    public static class IncompatibleKubernetesVersionException extends IllegalStateException {

    }


    @Override
    public Void call() throws Exception {
        final VersionInfo versionInfo = versionApi.getCode();

        logger.debug("Server version: {}", versionInfo);

        if (options.noVersionCheck) {
            logger.warn("Skipping K8s version check.");
            return null;
        }

        final int major = Integer.parseInt(versionInfo.getMajor().replaceAll("\\D+", ""));
        final int minor = Integer.parseInt(versionInfo.getMinor().replaceAll("\\D+", ""));

        if (major < 1) {
            // waaaay to old
            throw new IncompatibleKubernetesVersionException();
        }

        if (major == 1 && minor < 5) {
            // StatefulSets were introduced in 1.5
            throw new IncompatibleKubernetesVersionException();
        }

        if ((major == 1 && minor > 10) || major > 1) {
            logger.warn("The cassandra-operator has not been validated to work on this version of Kubernetes.");
        }

        return null;
    }
}
