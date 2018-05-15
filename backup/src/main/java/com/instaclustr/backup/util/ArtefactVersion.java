package com.instaclustr.backup.util;

import com.google.common.base.Joiner;

import java.util.Objects;

public class ArtefactVersion implements Comparable<ArtefactVersion> {
    public final int major;
    public final int minor;
    public final int patch;

    public ArtefactVersion(final int major, final int minor, final int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public ArtefactVersion(final String versionString) {
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
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse artefact version from provided string, format was correct but contained non integer values.");
            }
        }
        else
        {
            throw new IllegalArgumentException("Unable to parse artefact version from provided string, supported formats are '1,1,1' or '1.1.1' ");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ArtefactVersion that = (ArtefactVersion) o;
        return Objects.equals(major, that.major) &&
                Objects.equals(minor, that.minor) &&
                Objects.equals(patch, that.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }


    @Override
    public int compareTo(final ArtefactVersion that) {
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
