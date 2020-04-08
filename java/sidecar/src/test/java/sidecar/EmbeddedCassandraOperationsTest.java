package sidecar;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static sidecar.DatabaseHelper.createKeyspaceAndTable;

import java.util.Collections;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.nosan.embedded.cassandra.api.connection.CqlSessionCassandraConnectionFactory;
import com.instaclustr.cassandra.sidecar.operations.cleanup.CleanupOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.drain.DrainOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.flush.FlushOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.refresh.RefreshOperationRequest;
import com.instaclustr.cassandra.sidecar.operations.scrub.ScrubOperationRequest;
import org.testng.annotations.Test;

public class EmbeddedCassandraOperationsTest extends AbstractCassandraSidecarTest {

    private static final String keyspaceName = "testkeyspace";
    private static final String tableName = "testtable";

    @Test
    public void test() {
        createTable();
        insertData();

        sidecarClient.flush(new FlushOperationRequest(keyspaceName, Collections.singleton(tableName)));
        sidecarClient.flush(new FlushOperationRequest(keyspaceName, Collections.EMPTY_SET));
        sidecarClient.refresh(new RefreshOperationRequest(keyspaceName, tableName));
        sidecarClient.cleanup(new CleanupOperationRequest(keyspaceName, Collections.singleton(tableName), 0));
        sidecarClient.scrub(new ScrubOperationRequest(false, false, false, false, 0, keyspaceName, Collections.EMPTY_SET));
        sidecarClient.drain(new DrainOperationRequest());
    }

    private void insertData() {
        try (CqlSession session = new CqlSessionCassandraConnectionFactory().create(getCassandraInstance()).getConnection()) {
            session.execute(insertInto(keyspaceName, tableName)
                                .value("id", literal("1"))
                                .value("name", literal("stefan1"))
                                .build());
        }
    }

    private void createTable() {
        try (CqlSession session = new CqlSessionCassandraConnectionFactory().create(getCassandraInstance()).getConnection()) {
            createKeyspaceAndTable(session, keyspaceName, tableName);
        }
    }
}
