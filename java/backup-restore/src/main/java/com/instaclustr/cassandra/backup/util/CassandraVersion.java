package com.instaclustr.cassandra.backup.util;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ReflectionException;
import java.io.IOException;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.VersionNumber;
import com.instaclustr.version.ArtifactVersion;
import jmx.org.apache.cassandra.CassandraObjectNames;

// TODO - this class is currently not used anywhere (except tests)
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
        ArtifactVersion av = new ArtifactVersion(vn.getMajor(), vn.getMinor(), vn.getPatch());

        if (av.compareTo(new ArtifactVersion(3, 0, 0)) >= 0)
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
        final String releaseVersion = (String) connection.getAttribute(CassandraObjectNames.STORAGE_SERVICE_MBEAN_NAME, "ReleaseVersion");
        final VersionNumber version = VersionNumber.parse(releaseVersion);
        return CassandraVersion.forVersionNumber(version);
    }

    @Override
    public String toString() {
        return String.format("%d.%d", major, minor);
    }
}
