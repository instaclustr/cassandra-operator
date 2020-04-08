package sidecar;

import static java.util.concurrent.TimeUnit.HOURS;

import javax.validation.Validation;
import javax.validation.Validator;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory;
import com.github.nosan.embedded.cassandra.api.Cassandra;
import com.github.nosan.embedded.cassandra.api.Version;
import com.github.nosan.embedded.cassandra.artifact.Artifact;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.instaclustr.cassandra.sidecar.Sidecar;
import com.instaclustr.measure.Time;
import com.instaclustr.operations.SidecarClient;
import com.instaclustr.picocli.CassandraJMXSpec;
import com.instaclustr.picocli.typeconverter.CassandraJMXServiceURLTypeConverter;
import com.instaclustr.sidecar.http.JerseyHttpServerService;
import com.instaclustr.sidecar.picocli.SidecarSpec;
import com.instaclustr.sidecar.picocli.SidecarSpec.HttpServerInetSocketAddressTypeConverter;
import org.awaitility.Awaitility;
import org.glassfish.jersey.server.ResourceConfig;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

public abstract class AbstractCassandraSidecarTest {

    private static final String CASSANDRA_VERSION = System.getProperty("backup.tests.cassandra.version", "3.11.6");

    private static Artifact CASSANDRA_ARTIFACT = Artifact.ofVersion(Version.of(CASSANDRA_VERSION));

    private final Path cassandraDir = new File("target/cassandra").toPath().toAbsolutePath();

    private Cassandra cassandraInstance;

    @Inject
    ResourceConfig resourceConfig;

    @Inject
    ObjectMapper objectMapper;

    JerseyHttpServerService serverService;

    SidecarClient sidecarClient;

    Validator validator;

    @BeforeTest
    public void setup() throws Exception {
        startCassandra();
        startSidecar();
    }

    @AfterTest
    public void teardown() throws Exception {
        stopSidecar();
        stopCassandra();
    }

    protected List<Module> getModules() {
        return ImmutableList.of();
    }

    private void startCassandra() {
        EmbeddedCassandraFactory cassandraInstanceFactory = new EmbeddedCassandraFactory();
        cassandraInstanceFactory.setWorkingDirectory(cassandraDir);
        cassandraInstanceFactory.setArtifact(CASSANDRA_ARTIFACT);
        cassandraInstanceFactory.getJvmOptions().add("-Xmx1g");
        cassandraInstanceFactory.getJvmOptions().add("-Xms1g");
        cassandraInstanceFactory.setJmxLocalPort(7199);

        cassandraInstance = cassandraInstanceFactory.create();

        cassandraInstance.start();
    }

    private void stopCassandra() {
        cassandraInstance.stop();
    }

    private void startSidecar() throws Exception {

        CassandraJMXSpec cassandraJMXSpec = new CassandraJMXSpec();
        cassandraJMXSpec.jmxServiceURL = new CassandraJMXServiceURLTypeConverter().convert("service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi");

        SidecarSpec sidecarSpec = new SidecarSpec();
        sidecarSpec.httpServerAddress = new HttpServerInetSocketAddressTypeConverter().convert("127.0.0.1:4567");
        sidecarSpec.operationsExpirationPeriod = new Time(1L, HOURS);

        Sidecar sidecar = new Sidecar();
        sidecar.jmxSpec = cassandraJMXSpec;
        sidecar.sidecarSpec = sidecarSpec;

        final Injector injector = Guice.createInjector(sidecar.getModules());

        injector.injectMembers(this);

        serverService = new JerseyHttpServerService(new InetSocketAddress("localhost", 4567), resourceConfig);

        sidecarClient = new SidecarClient.Builder()
            .withHostname(serverService.getServerInetAddress().getHostName())
            .withPort(serverService.getServerInetAddress().getPort())
            .build(resourceConfig);

        validator = Validation.byDefaultProvider().configure().buildValidatorFactory().getValidator();

        final AtomicBoolean started = new AtomicBoolean(false);

        serverService.addListener(new Service.Listener() {
            @Override
            public void running() {
                started.set(true);
            }
        }, MoreExecutors.directExecutor());

        serverService.startAsync();

        Awaitility.await().until(started::get);
    }

    private void stopSidecar() {
        sidecarClient.close();
        serverService.stopAsync().awaitTerminated();
    }

    protected Cassandra getCassandraInstance() {
        return cassandraInstance;
    }
}
