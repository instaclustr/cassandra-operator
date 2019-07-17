package com.instaclustr.version;

import java.util.Objects;

import com.google.common.base.Joiner;

public class ArtifactVersion implements Comparable<ArtifactVersion> {
    public final int major;
    public final int minor;
    public final int patch;

    public ArtifactVersion(final int major, final int minor, final int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public ArtifactVersion(final String versionString) {
        final String[] values;

        if (versionString.contains(".")) {
            values = versionString.split("\\.");
        } else if (versionString.contains(",")) {
            values = versionString.split(",");
        } else {
            values = null;
        }

        if (values != null && (values.length == 2 || values.length == 3)) {
            try {
                this.major = Integer.parseInt(values[0]);
                this.minor = Integer.parseInt(values[1]);

                if (values.length == 2) {
                    this.patch = 0;
                } else {
                    this.patch = Integer.parseInt(values[2]);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse artifact version from provided string, format was correct but contained non integer values.");
            }
        } else {
            throw new IllegalArgumentException("Unable to parse artifact version from provided string, supported formats are '1,1,1' or '1.1.1' ");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ArtifactVersion that = (ArtifactVersion) o;
        return Objects.equals(major, that.major) &&
                Objects.equals(minor, that.minor) &&
                Objects.equals(patch, that.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public int compareTo(final ArtifactVersion that) {
        if (this.major == that.major && this.minor == that.minor)
            return Integer.compare(this.patch, that.patch);

        if (this.major == that.major)
            return Integer.compare(this.minor, that.minor);

        return Integer.compare(this.major, that.major);
    }

    public String toString() {
        return Joiner.on('.').join(major, minor, patch);
    }
}
