package com.instaclustr.cassandra.backup.jmx;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.VersionNumber;
import com.instaclustr.cassandra.backup.util.ArtefactVersion;

import javax.management.*;
import java.io.IOException;

public enum CassandraVersion {
    TWO_ZERO(2, 0),
    TWO_ONE(2, 1),
    TWO_TWO(2, 2),
    THREE(3, 0);

    public int major;
    public int minor;

    CassandraVersion(final int major, final int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static CassandraVersion forVersionNumber(final VersionNumber vn) {
        ArtefactVersion av = new ArtefactVersion(vn.getMajor(), vn.getMinor(), vn.getPatch());

        if (av.compareTo(new ArtefactVersion(3 ,0 ,0)) >= 0)
            return THREE;

        for (final CassandraVersion v : CassandraVersion.values()) {
            if (v.major == av.major && v.minor == av.minor)
                return v;
        }

        throw new IllegalArgumentException("Unsupported Cassandra release version " + av.toString());
    }

    public static CassandraVersion forSession(final Session session) {
        final VersionNumber version = session.getCluster().getMetadata().getAllHosts().iterator().next().getCassandraVersion();
        return CassandraVersion.forVersionNumber(version);
    }

    public static CassandraVersion forMBeanServerConnection(final MBeanServerConnection connection) throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IOException {
        final String releaseVersion = (String) connection.getAttribute(CassandraObjectNames.STORAGE_SERVICE, "ReleaseVersion");
        final VersionNumber version = VersionNumber.parse(releaseVersion);
        return CassandraVersion.forVersionNumber(version);
    }

    @Override
    public String toString() {return String.format("%d.%d", major, minor);}
}
