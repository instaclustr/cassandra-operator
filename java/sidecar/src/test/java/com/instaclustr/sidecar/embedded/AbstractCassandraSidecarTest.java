package com.instaclustr.sidecar.embedded;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory;
import com.github.nosan.embedded.cassandra.api.Cassandra;
import com.github.nosan.embedded.cassandra.api.Version;
import com.github.nosan.embedded.cassandra.artifact.Artifact;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.instaclustr.cassandra.sidecar.Sidecar;
import com.instaclustr.measure.Time;
import com.instaclustr.operations.SidecarClient;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter;
import com.instaclustr.sidecar.picocli.SidecarSpec;
import com.instaclustr.sidecar.picocli.SidecarSpec.HttpServerInetSocketAddressTypeConverter;
import org.awaitility.Duration;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

public abstract class AbstractCassandraSidecarTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCassandraSidecarTest.class);

    private static final String CASSANDRA_VERSION = System.getProperty("backup.tests.cassandra.version", "3.11.6");

    private static Artifact CASSANDRA_ARTIFACT = Artifact.ofVersion(Version.of(CASSANDRA_VERSION));

    private final Path cassandraDir = new File("target/cassandra").toPath().toAbsolutePath();

    protected static final String keyspaceName = "testkeyspace";
    protected static final String tableName = "testtable";
    protected static final int numberOfSSTables = 3;

    protected Map<String, Cassandra> cassandraInstances = new TreeMap<>();
    protected Map<String, SidecarPair> sidecars = new TreeMap<>();

    protected SidecarClient sidecarClient;
    protected DatabaseHelper dbHelper;

    @BeforeClass
    public void beforeClass() throws Exception {
        startNodes();
        startSidecars();

        dbHelper = new DatabaseHelper(cassandraInstances, sidecars);
        sidecarClient = sidecars.get("datacenter1").sidecarClient;
    }

    @AfterClass
    public void afterClass() {
        stopSidecars();
        stopNodes();
        waitForClosedPort(7199);
        waitForClosedPort("127.0.0.1", 7200);
        waitForClosedPort("127.0.0.2", 7200);
    }

    @BeforeMethod
    public void beforeMethod() {
        dbHelper.createKeyspaceAndTable(keyspaceName, tableName);

        for (int i = 0; i < numberOfSSTables; i++) {
            dbHelper.addDataAndFlush(keyspaceName, tableName);
            dbHelper.addDataAndFlush(keyspaceName, tableName);
            dbHelper.addDataAndFlush(keyspaceName, tableName);
        }
    }

    @AfterMethod
    public void afterMethod() {
        try {
            dbHelper.dropKeyspace(keyspaceName);
        } catch (Exception ex) {
            // intentionally empty
        }
    }

    protected EmbeddedCassandraFactory defaultNodeFactory() {
        EmbeddedCassandraFactory cassandraInstanceFactory = new EmbeddedCassandraFactory();
        cassandraInstanceFactory.setWorkingDirectory(cassandraDir);
        cassandraInstanceFactory.setArtifact(CASSANDRA_ARTIFACT);
        cassandraInstanceFactory.getJvmOptions().add("-Xmx1g");
        cassandraInstanceFactory.getJvmOptions().add("-Xms1g");
        cassandraInstanceFactory.setJmxLocalPort(7199);

        return cassandraInstanceFactory;
    }

    protected Map<String, Cassandra> customNodes() throws Exception {
        return Collections.emptyMap();
    }

    protected void startNodes() {

        try {
            Map<String, Cassandra> customNodes = customNodes();

            if (customNodes.isEmpty()) {
                cassandraInstances.put("datacenter1", defaultNodeFactory().create());
            } else {
                cassandraInstances.putAll(customNodes);
            }

            cassandraInstances.values().forEach(Cassandra::start);
        } catch (Exception ex) {
            logger.error("Some nodes could not be started. Be sure you have 127.0.0.2 interface too for tests which are starting with multiple Cassandra instances.");
        }
    }

    protected void stopNodes() {
        List<Cassandra> nodes = new ArrayList<>(cassandraInstances.values());
        Collections.reverse(nodes);
        nodes.forEach(Cassandra::stop);
    }

    protected void startSidecars() throws Exception {

        Map<String, SidecarPair> customSidecars = customSidecars();

        sidecars.clear();

        if (customSidecars.isEmpty()) {
            sidecars.put("datacenter1", defaultSidecar());
        } else {
            sidecars.putAll(customSidecars);
        }
    }

    protected void stopSidecars() {
        List<SidecarPair> serverServices = new ArrayList<>(sidecars.values());

        Collections.reverse(serverServices);
        serverServices.forEach(pair -> {
            try {
                pair.serviceManager.stopAsync().awaitStopped();
                pair.sidecarClient.close();
                waitForClosedPort(pair.sidecarClient.getPort());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    protected Map<String, SidecarPair> customSidecars() throws Exception {
        return Collections.emptyMap();
    }

    protected SidecarPair sidecar(String address, Integer jmxPort, Integer httpPort) throws Exception {
        CassandraJMXSpec cassandraJMXSpec = new CassandraJMXSpec();
        cassandraJMXSpec.jmxServiceURL = new CassandraJMXServiceURLTypeConverter().convert("service:jmx:rmi:///jndi/rmi://" + address + ":" + jmxPort.toString() + "/jmxrmi");

        SidecarSpec sidecarSpec = new SidecarSpec();
        sidecarSpec.httpServerAddress = new HttpServerInetSocketAddressTypeConverter().convert("127.0.0.1:" + httpPort.toString());
        sidecarSpec.operationsExpirationPeriod = new Time(1L, HOURS);

        Injector injector = Guice.createInjector(new Sidecar().getModules(sidecarSpec, cassandraJMXSpec));

        ServiceManager serviceManager = injector.getInstance(ServiceManager.class);

        serviceManager.startAsync().awaitHealthy();

        waitForOpenPort(httpPort);

        SidecarClient sidecarClient = new SidecarClient.Builder().withHostname(address).withPort(httpPort).build(injector.getInstance(ResourceConfig.class));

        return new SidecarPair(serviceManager, sidecarClient);
    }

    protected SidecarPair defaultSidecar() throws Exception {
        return sidecar("127.0.0.1", 7199, 4567);
    }

    protected void waitForClosedPort(String hostname, int port) {
        await().timeout(Duration.FIVE_MINUTES).until(() -> {
            try {
                (new Socket(hostname, port)).close();
                return false;
            } catch (SocketException e) {
                return true;
            }
        });
    }

    protected void waitForClosedPort(int port) {
        waitForClosedPort("127.0.0.1", port);
    }

    protected void waitForOpenPort(String hostname, int port) {
        await().until(() -> {
            try {
                (new Socket("127.0.0.1", port)).close();
                return true;
            } catch (SocketException e) {
                return false;
            }
        });
    }

    protected void waitForOpenPort(int port) {
        waitForOpenPort("127.0.0.1", port);
    }

    public static class SidecarPair {

        public final ServiceManager serviceManager;
        public final SidecarClient sidecarClient;

        public SidecarPair(final ServiceManager serviceManager, final SidecarClient sidecarClient) {
            this.serviceManager = serviceManager;
            this.sidecarClient = sidecarClient;
        }
    }
}
