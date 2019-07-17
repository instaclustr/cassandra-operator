package com.instaclustr.cassandra.backup.impl.restore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class RestorePredicates {
    public static class KeyspaceTable {
        enum TableType {
            SYSTEM,
            SYSTEM_AUTH,
            SCHEMA,
            OTHER
        }

        final String keyspace;
        final String table;
        final TableType tableType;

        KeyspaceTable(final String keyspace, final String table) {
            this.keyspace = keyspace;
            this.table = table;
            this.tableType = classifyTable(keyspace, table);
        }

        public TableType classifyTable(final String keyspace, final String table) {
            if (keyspace.equals("system") && !table.startsWith("schema_")) {
                return TableType.SYSTEM;
            } else if (keyspace.equals("system_schema") ||
                    (keyspace.equals("system") && table.startsWith("schema_"))) {
                return TableType.SCHEMA;
            } else if (keyspace.equals("system_auth")) {
                return TableType.SYSTEM_AUTH;
            } else {
                return TableType.OTHER;
            }
        }
    }

    public static Optional<KeyspaceTable> getKeyspaceTableFromManifestPath(final Logger logger, final String manifestLine) {
        final Path manifestPath = getManifestPath(manifestLine);

        if (!manifestPath.getName(0).toString().equals("data")) {
            logger.info("Skipping non-data directory {}", manifestPath.toString());
            return Optional.empty(); // don't download non "C*/data" data
        }

        return Optional.of(new KeyspaceTable(manifestPath.getName(1).toString(),
                                             StringUtils.split(manifestPath.getName(2).toString(), '-')[0]));
    }

    public static Path getManifestPath(final String manifestLine) {
        final String[] lineArray = manifestLine.trim().split(" ");

        if (lineArray.length != 2) {
            throw new IllegalArgumentException(String.format("Invalid snapshot manifest line: %s", manifestLine));
        }

        return Paths.get(lineArray[1]);
    }

    static Predicate<Path> isSubsetTable(final Multimap<String, String> keyspaceTableSubset) {
        // Subset restore on existing cluster, so only clear out the tables we're restoring
        return p -> {
//            differentiating the paths whenever they contain secondary indexes
            if (isSecondaryIndex(p)) {
                final KeyspaceTable ktSecIndex = new KeyspaceTable(p.getParent().getParent().getParent().getFileName().toString(),
                                                                   StringUtils.split(p.getParent().getParent().getFileName().toString(), "-")[0]);
                return keyspaceTableSubset.containsEntry(ktSecIndex.keyspace, ktSecIndex.table);
            } else {
                final KeyspaceTable kt = new KeyspaceTable(p.getParent().getParent().getFileName().toString(),
                                                           StringUtils.split(p.getParent().getFileName().toString(), '-')[0]);
                return keyspaceTableSubset.containsEntry(kt.keyspace, kt.table);
            }
        };
    }

    private static boolean isSecondaryIndex(final Path p) {
        return p.getParent().getFileName().toString().startsWith(".");
    }

    // Ex. manifest line:
    // 3.11: 38 data/test/testuncompressed-ce555490463111e7be3e3d534d5cadea/1-1160807146/mc-1-big-Digest.crc32
    // 2.2:  38 data/test/testuncompressed-37f71aca7dc2383ba70672528af04d4f/1-2632208265/test-testuncompressed-jb-1-Data.db
    // 2.0:  38 data/test/testuncompressed/1-2569865052/test-testuncompressed-jb-1-Data.db
    public static Predicate<String> getManifestFilesAllExceptSystem(final Logger logger) {
        return m -> {
            final Optional<KeyspaceTable> ktOpt = getKeyspaceTableFromManifestPath(logger, m);

            return ktOpt.map(kt -> kt.tableType != KeyspaceTable.TableType.SYSTEM)
                    .orElse(false);
        };
    }

    public static Predicate<String> getManifestFilesForFullExistingRestore(final Logger logger) {
        // Full restore on existing cluster, so download:
        // 3.0, 3.1: system_distributed, system_traces, system_schema, system_auth, custom keyspaces
        // 2.0, 2.1, 2.2: system_distributed, system_traces, system_auth, system (only schema_ tables)
        return getManifestFilesAllExceptSystem(logger);
    }

    public static Predicate<String> getManifestFilesForFullNewRestore(final Logger logger) {
        // Full restore on new cluster, so download:
        // 3.0, 3.1: system_distributed, system_traces, system_schema, system_auth, custom keyspaces
        // 2.0, 2.1, 2.2: system_distributed, system_traces, system_auth, system (only schema_ tables)
        return getManifestFilesAllExceptSystem(logger);
    }

    public static Predicate<String> getManifestFilesForSubsetExistingRestore(final Logger logger, final Multimap<String, String> keyspaceTableSubset) {
        // Subset restore on existing cluster, so only download subset keyspace.tables.
        // Don't download schema files, so other tables will be unaffected (prefer possibility of PIT subset not matching current schema)
        return m -> {
            final Optional<KeyspaceTable> ktOpt = getKeyspaceTableFromManifestPath(logger, m);

            return ktOpt.map(kt -> keyspaceTableSubset.containsEntry(kt.keyspace, kt.table))
                    .orElse(false);
        };
    }

    public static Predicate<String> getManifestFilesForSubsetNewRestore(final Logger logger, final Multimap<String, String> keyspaceTableSubset) {
        // Subset restore on new cluster. Download subset keyspace.tables and:
        // 3.0, 3.1: system_schema, system_auth
        // 2.0, 2.1, 2.2: system_auth, system (only schema_)
        return m -> {
            final Optional<KeyspaceTable> ktOpt = getKeyspaceTableFromManifestPath(logger, m);

            return ktOpt.map(kt -> kt.tableType == KeyspaceTable.TableType.SYSTEM_AUTH ||
                    kt.tableType == KeyspaceTable.TableType.SCHEMA ||
                    keyspaceTableSubset.containsEntry(kt.keyspace, kt.table))
                    .orElse(false);
        };
    }
}
