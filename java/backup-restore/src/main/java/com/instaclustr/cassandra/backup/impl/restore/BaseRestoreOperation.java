package com.instaclustr.cassandra.backup.impl.restore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import com.google.common.base.Joiner;
import com.instaclustr.cassandra.backup.impl.RemoteObjectReference;
import com.instaclustr.sidecar.operations.Operation;

public abstract class BaseRestoreOperation<T extends RestoreOperationRequest> extends Operation<T> {

    protected BaseRestoreOperation(final T request) {
        super(request);
    }

    protected void writeConfigOptions(final Restorer restorer, final boolean isTableSubsetOnly) throws Exception {
        final StringBuilder cassandraEnvStringBuilder = new StringBuilder();

        // was if (enableCommitLogRestore)
        if (false) {
            if (isTableSubsetOnly) {
                cassandraEnvStringBuilder
                        .append("JVM_OPTS=\"$JVM_OPTS -Dcassandra.replayList=")
                        .append(Joiner.on(",").withKeyValueSeparator(".").join(request.keyspaceTables.entries()))
                        .append("\"\n");
            }

            Files.write(request.cassandraConfigDirectory.resolve("cassandra-env.sh"), cassandraEnvStringBuilder.toString().getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        }

        //TODO: re-enable once cascading yaml loader lands
        // Can't write to cassandra-env.sh as nodetool could generate "File name too long" error
        RemoteObjectReference tokens = restorer.objectKeyToRemoteReference(Paths.get("tokens/" + request.snapshotTag + "-tokens.yaml"));
        final Path tokensPath = request.cassandraDirectory.resolve("tokens.yaml");
        restorer.downloadFile(tokensPath, tokens);
//
//        final StringBuilder stringBuilder = new StringBuilder();
//        final String contents = new String(Files.readAllBytes(cassandraConfigDirectory.resolve("cassandra.yaml")));
//        // Just replace. In case nodepoint later doesn't write auto_bootstrap, just delete here and append later to guarantee we're setting it
//        stringBuilder.append(contents.replace("auto_bootstrap: true", ""));
//
//        stringBuilder.append(System.lineSeparator());
//        stringBuilder.append(new String(Files.readAllBytes(tokensPath)));
//        // Don't stream on Cassandra startup, as tokens and SSTables are already present on node
//        stringBuilder.append("auto_bootstrap: false");
//        Files.write(cassandraConfigDirectory.resolve("cassandra.yaml"), ImmutableList.of(stringBuilder.toString()), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }
}
