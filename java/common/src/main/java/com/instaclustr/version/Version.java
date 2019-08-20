package com.instaclustr.version;

import com.google.common.base.MoreObjects;

public class Version {
    public final String version;
    public final String buildTime;
    public final String gitCommit;

    public Version(final String version, final String buildTime, final String gitCommit) {
        this.version = version;
        this.buildTime = buildTime;
        this.gitCommit = gitCommit;
    }

    public static Version parse(final String[] versionArray) {
        String version = "unknown";
        String buildTime = "unknown";
        String gitCommit = "unknown";

        if (versionArray != null) {
            if (versionArray.length > 0) {
                version = versionArray[0];
            }

            if (versionArray.length > 1 && versionArray[1] != null) {
                buildTime = parseEntry(versionArray[1]);
            }

            if (versionArray.length > 2 && versionArray[2] != null) {
                gitCommit = parseEntry(versionArray[2]);
            }
        }

        return new Version(version, buildTime, gitCommit);
    }

    private static String parseEntry(final String entry) {
        if (entry.contains(":")) {
            return entry.substring(entry.indexOf(":") + 1).trim();
        } else {
            return entry;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("version", version)
                          .add("buildTime", buildTime)
                          .add("gitCommit", gitCommit)
                          .toString();
    }
}
