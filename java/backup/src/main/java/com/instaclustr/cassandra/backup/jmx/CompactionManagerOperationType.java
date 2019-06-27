package com.instaclustr.cassandra.backup.jmx;

//Based off org.apache.cassandra.db.compaction.OperationType for 2.0
//2.0 has the least choices, 2.2 the most, change & add if necessary

public enum CompactionManagerOperationType {

    COMPACTION,
    VALIDATION,
    KEY_CACHE_SAVE,
    ROW_CACHE_SAVE,
    COUNTER_CACHE_SAVE,
    CLEANUP,
    SCRUB,
    UPGRADE_SSTABLES,
    INDEX_BUILD,
    /** Compaction for tombstone removal */
    TOMBSTONE_COMPACTION,
    UNKNOWN;

}
