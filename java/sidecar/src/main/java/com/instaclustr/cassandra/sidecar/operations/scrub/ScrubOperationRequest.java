package com.instaclustr.cassandra.sidecar.operations.scrub;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.sidecar.operations.OperationRequest;

/**
 * $ nodetool help scrub
 * NAME
 *         nodetool scrub - Scrub (rebuild sstables for) one or more tables
 *
 * SYNOPSIS
 *         nodetool [(-h <host> | --host <host>)] [(-p <port> | --port <port>)]
 *                 [(-pw <password> | --password <password>)]
 *                 [(-pwf <passwordFilePath> | --password-file <passwordFilePath>)]
 *                 [(-u <username> | --username <username>)] scrub
 *                 [(-j <jobs> | --jobs <jobs>)] [(-n | --no-validate)]
 *                 [(-ns | --no-snapshot)] [(-r | --reinsert-overflowed-ttl)]
 *                 [(-s | --skip-corrupted)] [--] [<keyspace> <tables>...]
 *
 * OPTIONS
 *         -h <host>, --host <host>
 *             Node hostname or ip address
 *
 *         -j <jobs>, --jobs <jobs>
 *             Number of sstables to scrub simultanously, set to 0 to use all
 *             available compaction threads
 *
 *         -n, --no-validate
 *             Do not validate columns using column validator
 *
 *         -ns, --no-snapshot
 *             Scrubbed CFs will be snapshotted first, if disableSnapshot is false.
 *             (default false)
 *
 *         -p <port>, --port <port>
 *             Remote jmx agent port number
 *
 *         -pw <password>, --password <password>
 *             Remote jmx agent password
 *
 *         -pwf <passwordFilePath>, --password-file <passwordFilePath>
 *             Path to the JMX password file
 *
 *         -r, --reinsert-overflowed-ttl
 *             Rewrites rows with overflowed expiration date affected by
 *             CASSANDRA-14092 with the maximum supported expiration date of
 *             2038-01-19T03:14:06+00:00. The rows are rewritten with the original
 *             timestamp incremented by one millisecond to override/supersede any
 *             potential tombstone that may have been generated during compaction
 *             of the affected rows.
 *
 *         -s, --skip-corrupted
 *             Skip corrupted partitions even when scrubbing counter tables.
 *             (default false)
 *
 *         -u <username>, --username <username>
 *             Remote jmx agent username
 *
 *         --
 *             This option can be used to separate command-line options from the
 *             list of argument, (useful when arguments might be mistaken for
 *             command-line options
 *
 *         [<keyspace> <tables>...]
 *             The keyspace followed by one or many tables
 */
public class ScrubOperationRequest extends OperationRequest {

    // these are in nodetool initialised to "false", hence null is fine for us
    public final boolean disableSnapshot;
    public final boolean skipCorrupted;
    public final boolean noValidate;
    public final boolean reinsertOverflowedTTL;

    @Min(0)
    public final int jobs;

    @NotEmpty
    public final String keyspace;

    public final Set<String> tables;

    @JsonCreator
    public ScrubOperationRequest(@JsonProperty("disableSnapshot") final boolean disableSnapshot,
                                 @JsonProperty("skipCorrupted") final boolean skipCorrupted,
                                 @JsonProperty("noValidate") final boolean noValidate,
                                 @JsonProperty("reinsertOverflowedTTL") final boolean reinsertOverflowedTTL,
                                 @JsonProperty("jobs") final int jobs,
                                 @JsonProperty("keyspace") final String keyspace,
                                 @JsonProperty("tables") final Set<String> tables) {
        this.jobs = jobs;
        this.keyspace = keyspace;
        this.tables = tables;
        this.disableSnapshot = disableSnapshot;
        this.skipCorrupted = skipCorrupted;
        this.noValidate = noValidate;
        this.reinsertOverflowedTTL = reinsertOverflowedTTL;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("keyspace", keyspace)
                .add("tables", tables)
                .add("jobs", jobs)
                .add("disableSnapshot", disableSnapshot)
                .add("skipCorrupted", skipCorrupted)
                .add("noValidate", noValidate)
                .add("reinsertOverflowedTTL", reinsertOverflowedTTL)
                .toString();
    }
}
