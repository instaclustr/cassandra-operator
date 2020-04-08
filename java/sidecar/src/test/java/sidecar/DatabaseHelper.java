package sidecar;

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.google.common.collect.ImmutableMap.of;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

public class DatabaseHelper {

    public static void createKeyspace(CqlSession session, String keyspace) {
        session.execute(SchemaBuilder.createKeyspace(keyspace)
                            .ifNotExists()
                            .withNetworkTopologyStrategy(of("datacenter1", 1))
                            .build());
    }

    public static void createKeyspaceAndTable(CqlSession session, String keyspace, String table) {
        createKeyspace(session, keyspace);
        createTable(session, keyspace, table);
    }

    public static void createTable(CqlSession session, String keyspace, String table) {
        session.execute(SchemaBuilder.createTable(keyspace, table)
                            .ifNotExists()
                            .withPartitionKey("id", TEXT)
                            .withColumn("name", TEXT)
                            .build());

    }
}
