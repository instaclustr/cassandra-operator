package com.instaclustr.version;

import com.google.common.base.MoreObjects;

public class Version {
    public final String version;

    public Version(final String[] version) {
        if (version == null) {
            this.version = "unknown";
        } else {
            this.version = String.join(", ", version);
        }
    }

    public Version(final String version) {
        this(new String[]{version});
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("version", version)
                .toString();
    }
}
