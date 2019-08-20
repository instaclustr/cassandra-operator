package com.instaclustr.cassandra.backup;

import jmx.org.apache.cassandra.CassandraVersion;

public class TestFileConfig {
    public String sha1Hash;
    public final CassandraVersion cassandraVersion;
    public final String sstableVersion;

    public TestFileConfig(final String sha1Hash, final CassandraVersion cassandraVersion) {
        this.sha1Hash = sha1Hash;
        this.cassandraVersion = cassandraVersion;

        if (CassandraVersion.isThree(cassandraVersion)) {
            this.sstableVersion = "mb";
        } else if (CassandraVersion.isTwoTwo(cassandraVersion)) {
            this.sstableVersion = "lb";
        } else {
            this.sstableVersion = "jb";
        }
    }

    public String getSstablePrefix(final String keyspace, final String table) {
        if (CassandraVersion.isTwoZero(cassandraVersion)) {
            return String.format("%s-%s-%s", keyspace, table, sstableVersion);
        }

        return sstableVersion;
    }

    public String getChecksum(final String keyspace, final String table) {
        if (CassandraVersion.isTwoZero(cassandraVersion)) {
            return String.format("%s  %s-%s-%s-1-Data.db", sha1Hash, keyspace, table, sstableVersion);
        }

        // 2.1 sha1 contains just checksum (compressed and uncompressed)
        if (CassandraVersion.isTwoOne(cassandraVersion)) {
            return sha1Hash;
        }

        // 2.2 adler32 contains just checksum (compressed and uncompressed)
        // 3.0 and 3.1 crc32 contains just checksum (compressed and uncompressed)
        return Long.toString(1000000000L);
    }

    // 2.0 only creates digest files for un-compressed SSTables
    public boolean createDigest(final boolean isCompressed) {
        return !CassandraVersion.isTwoZero(cassandraVersion) || !isCompressed;
    }
}