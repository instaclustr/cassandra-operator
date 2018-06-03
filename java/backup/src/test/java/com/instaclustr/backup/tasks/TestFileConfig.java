package com.instaclustr.backup.tasks;

import com.instaclustr.backup.jmx.CassandraVersion;

public class TestFileConfig {
    public String sha1Hash;
    public final CassandraVersion cassandraVersion;
    public final String sstableVersion;

    public TestFileConfig(final String sha1Hash, final CassandraVersion cassandraVersion) {
        this.sha1Hash = sha1Hash;
        this.cassandraVersion = cassandraVersion;

        if (cassandraVersion == CassandraVersion.THREE) {
            this.sstableVersion = "mb";
        } else if (cassandraVersion == CassandraVersion.TWO_TWO) {
            this.sstableVersion = "lb";
        } else {
            this.sstableVersion = "jb";
        }
    }

    public String getSstablePrefix(final String keyspace, final String table) {
        if (this.cassandraVersion == CassandraVersion.TWO_ZERO) {
            return String.format("%s-%s-%s", keyspace, table, this.sstableVersion);
        }

        return this.sstableVersion;
    }

    public String getChecksum(final String keyspace, final String table) {
        if (this.cassandraVersion == CassandraVersion.TWO_ZERO) {
            return String.format("%s  %s-%s-%s-1-Data.db", sha1Hash, keyspace, table, this.sstableVersion);
        }

        // 2.1 sha1 contains just checksum (compressed and uncompressed)
        if (this.cassandraVersion == CassandraVersion.TWO_ONE) {
            return sha1Hash;
        }

        // 2.2 adler32 contains just checksum (compressed and uncompressed)
        // 3.0 and 3.1 crc32 contains just checksum (compressed and uncompressed)
        return Long.toString(1000000000L);
    }

    // 2.0 only creates digest files for un-compressed SSTables
    public boolean createDigest(final boolean isCompressed) {
        return cassandraVersion != CassandraVersion.TWO_ZERO || !isCompressed;
    }
}
