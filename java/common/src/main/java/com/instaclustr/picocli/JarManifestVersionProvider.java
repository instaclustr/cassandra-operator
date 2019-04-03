package com.instaclustr.picocli;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class JarManifestVersionProvider implements CommandLine.IVersionProvider {
    private final String implementationTitle;

    protected JarManifestVersionProvider(final String implementationTitle) {
        this.implementationTitle = implementationTitle;
    }

    @Override
    public String[] getVersion() throws IOException {
        final Enumeration<URL> resources = CommandLine.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

        Optional<String> implementationVersion = Optional.empty();
        Optional<String> buildTime = Optional.empty();
        Optional<String> gitCommit = Optional.empty();

        while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();

            final Manifest manifest = new Manifest(url.openStream());
            final Attributes attributes = manifest.getMainAttributes();

            if (isApplicableManifest(attributes)) {
                implementationVersion = Optional.ofNullable(attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));
                buildTime = Optional.ofNullable(attributes.getValue("Build-Time"));
                gitCommit = Optional.ofNullable(attributes.getValue("Git-Commit"));

                break;
            }
        }

        return new String[] {
                String.format("%s %s", implementationTitle, implementationVersion.orElse("development build")),
                String.format("Build time: %s", buildTime.orElse("unknown")),
                String.format("Git commit: %s", gitCommit.orElse("unknown")),
        };
    }

    private boolean isApplicableManifest(Attributes attributes) {
        return implementationTitle.equals(attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE));
    }

    public static void logCommandVersionInformation(final CommandLine.Model.CommandSpec commandSpec) {
        final Logger logger = LoggerFactory.getLogger(commandSpec.userObject().getClass());

        logger.info("{} version: {}", commandSpec.name(), Joiner.on(", ").join(commandSpec.version()));
    }
}
