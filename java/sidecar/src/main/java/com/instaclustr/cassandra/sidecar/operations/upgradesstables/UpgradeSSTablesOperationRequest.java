package com.instaclustr.cassandra.sidecar.operations.upgradesstables;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.sidecar.operations.OperationRequest;

/**
 * $ nodetool help upgradesstables
 * NAME
 *         nodetool upgradesstables - Rewrite sstables (for the requested tables)
 *         that are not on the current version (thus upgrading them to said current
 *         version)
 *
 * SYNOPSIS
 *         nodetool [(-h <host> | --host <host>)] [(-p <port> | --port <port>)]
 *                 [(-pw <password> | --password <password>)]
 *                 [(-pwf <passwordFilePath> | --password-file <passwordFilePath>)]
 *                 [(-u <username> | --username <username>)] upgradesstables
 *                 [(-a | --include-all-sstables)] [(-j <jobs> | --jobs <jobs>)] [--]
 *                 [<keyspace> <tables>...]
 *
 * OPTIONS
 *         -a, --include-all-sstables
 *             Use -a to include all sstables, even those already on the current
 *             version
 *
 *         -h <host>, --host <host>
 *             Node hostname or ip address
 *
 *         -j <jobs>, --jobs <jobs>
 *             Number of sstables to upgrade simultanously, set to 0 to use all
 *             available compaction threads
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
public class UpgradeSSTablesOperationRequest extends OperationRequest {

    public final boolean includeAllSSTables;

    @Min(0)
    public final int jobs;

    @NotEmpty
    public final String keyspace;

    public final Set<String> tables;

    @JsonCreator
    public UpgradeSSTablesOperationRequest(@JsonProperty("keyspace") final String keyspace,
                                           @JsonProperty("tables") final Set<String> tables,
                                           @JsonProperty("includeAllSStables") final boolean includeAllSSTables,
                                           @JsonProperty("jobs") final int jobs) {
        this.jobs = jobs;
        this.keyspace = keyspace;
        this.tables = tables;
        this.includeAllSSTables = includeAllSSTables;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("keyspace", keyspace)
                .add("tables", tables)
                .add("jobs", jobs)
                .add("includeAllSSTables", includeAllSSTables)
                .toString();
    }
}