package com.instaclustr.sidecar.embedded.multinode;

import static org.testng.Assert.assertEquals;

import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory;
import com.github.nosan.embedded.cassandra.api.Cassandra;
import com.github.nosan.embedded.cassandra.commons.io.ClassPathResource;
import com.instaclustr.cassandra.sidecar.service.CassandraStatusService.Status.NodeState;
import com.instaclustr.operations.SidecarClient;
import com.instaclustr.sidecar.embedded.AbstractCassandraSidecarTest;
import org.testng.annotations.Test;

public class DecommissionTest extends AbstractCassandraSidecarTest {

    @Override
    protected Map<String, SidecarPair> customSidecars() throws Exception {
        return new TreeMap<String, SidecarPair>() {{
            put("datacenter1", sidecar("127.0.0.1", 7199, 4567));
            put("datacenter2", sidecar("127.0.0.1", 7200, 4568));
        }};
    }

    @Override
    protected Map<String, Cassandra> customNodes() throws Exception {

        EmbeddedCassandraFactory factory = defaultNodeFactory();

        factory.setRackConfig(new ClassPathResource("cassandra1-rackdc.properties"));
        factory.setWorkingDirectory(Files.createTempDirectory(null));
        factory.setConfig(new ClassPathResource("first.yaml"));
        factory.setJmxLocalPort(7199);

        Cassandra firstNode = factory.create();

        factory.setRackConfig(new ClassPathResource("cassandra2-rackdc.properties"));
        factory.setWorkingDirectory(Files.createTempDirectory(null));
        factory.setConfig(new ClassPathResource("second.yaml"));
        factory.setJmxLocalPort(7200);

        Cassandra secondNode = factory.create();

        return new TreeMap<String, Cassandra>() {{
            put("datacenter1", firstNode);
            put("datacenter2", secondNode);
        }};
    }

    @Test
    public void decommissionTest() {

        SidecarClient firstClient = sidecars.get("datacenter1").sidecarClient;
        SidecarClient secondClient = sidecars.get("datacenter2").sidecarClient;

        secondClient.waitForCompleted(secondClient.decommission());

        assertEquals(secondClient.getStatus().status.getNodeState(), NodeState.DECOMMISSIONED);
        assertEquals(firstClient.getStatus().status.getNodeState(), NodeState.NORMAL);
    }
}
