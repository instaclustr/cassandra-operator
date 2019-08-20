package com.instaclustr.cassandra.sidecar.operations.rebuild;

import javax.validation.constraints.NotEmpty;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.instaclustr.sidecar.operations.OperationRequest;

/**
 * $ nodetool help rebuild
 * NAME
 *         nodetool rebuild - Rebuild data by streaming from other nodes (similarly
 *         to bootstrap)
 *
 * SYNOPSIS
 *         nodetool [(-h <host> | --host <host>)] [(-p <port> | --port <port>)]
 *                 [(-pw <password> | --password <password>)]
 *                 [(-pwf <passwordFilePath> | --password-file <passwordFilePath>)]
 *                 [(-u <username> | --username <username>)] rebuild
 *                 [(-ks <specific_keyspace> | --keyspace <specific_keyspace>)]
 *                 [(-s <specific_sources> | --sources <specific_sources>)]
 *                 [(-ts <specific_tokens> | --tokens <specific_tokens>)] [--]
 *                 <src-dc-name>
 *
 * OPTIONS
 *         -h <host>, --host <host>
 *             Node hostname or ip address
 *
 *         -ks <specific_keyspace>, --keyspace <specific_keyspace>
 *             Use -ks to rebuild specific keyspace.
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
 *         -s <specific_sources>, --sources <specific_sources>
 *             Use -s to specify hosts that this node should stream from when -ts
 *             is used. Multiple hosts should be separated using commas (e.g.
 *             127.0.0.1,127.0.0.2,...)
 *
 *         -ts <specific_tokens>, --tokens <specific_tokens>
 *             Use -ts to rebuild specific token ranges, in the format of "(start_token_1,end_token_1],(start_token_2,end_token_2],...(start_token_n,end_token_n]".
 *
 *         -u <username>, --username <username>
 *             Remote jmx agent username
 *
 *         --
 *             This option can be used to separate command-line options from the
 *             list of argument, (useful when arguments might be mistaken for
 *             command-line options
 *
 *         <src-dc-name>
 *             Name of DC from which to select sources for streaming. By default,
 *             pick any DC
 */
@ValidRebuildOperationRequest
public class RebuildOperationRequest extends OperationRequest {

    public final String sourceDC;
    public final String keyspace;
    public final Set<TokenRange> specificTokens;
    public final Set<String> specificSources;

    @JsonCreator
    public RebuildOperationRequest(@JsonProperty("sourceDC") final String sourceDC,
                                   @JsonProperty("keyspace") final String keyspace,
                                   @JsonProperty("specificTokens") final Set<TokenRange> specificTokens,
                                   @JsonProperty("specificSources") final Set<String> specificSources) {
        this.sourceDC = sourceDC;
        this.keyspace = keyspace;
        this.specificTokens = specificTokens;
        this.specificSources = specificSources;
    }

    public static final class TokenRange {

        @NotEmpty
        public final String start;

        @NotEmpty
        public final String end;

        @JsonCreator
        public TokenRange(@JsonProperty("start") final String start,
                          @JsonProperty("end") final String end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(TokenRange.this)
                    .add("start", start)
                    .add("end", end)
                    .toString();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("keyspace", keyspace)
                .add("sourceDC", sourceDC)
                .add("specificTokens", specificTokens)
                .add("specificSources", specificSources)
                .toString();
    }
}
