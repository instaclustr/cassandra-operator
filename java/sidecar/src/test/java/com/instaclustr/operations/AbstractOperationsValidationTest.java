package com.instaclustr.operations;


import javax.validation.Validation;
import javax.validation.Validator;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.sidecar.http.JerseyHttpServerService;
import com.instaclustr.sidecar.operations.OperationsModule;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.ResourceConfig;
import org.mockito.Mockito;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

public abstract class AbstractOperationsValidationTest {

    static final String TEST_HOSTNAME = System.getProperty("SIDECAR_TEST_HOSTNAME", "localhost");

    static final int TEST_PORT = Integer.getInteger("SIDECAR_TEST_PORT", 8080);

    @Inject
    ResourceConfig resourceConfig;

    @Inject
    ObjectMapper objectMapper;

    JerseyHttpServerService serverService;

    Validator validator;

    protected abstract List<Module> getModules();

    @BeforeTest
    public void setup() {

        List<Module> modules = new ArrayList<Module>() {{
            add(new OperationsModule());
            add(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(StorageServiceMBean.class).toInstance(Mockito.mock(StorageServiceMBean.class));
                }
            });
            add(new JerseyHttpServerModule());
        }};

        modules.addAll(getModules());

        final Injector injector = Guice.createInjector(modules);

        injector.injectMembers(this);

        serverService = new JerseyHttpServerService(new InetSocketAddress(TEST_HOSTNAME, TEST_PORT), resourceConfig);

        validator = Validation.byDefaultProvider().configure().buildValidatorFactory().getValidator();
    }

    @AfterTest
    public void teardown() {
        serverService.stopAsync();
    }

    protected Pair<AtomicReference<List<SidecarClient.OperationResult<?>>>, AtomicBoolean> performOnRunningServer(final JerseyHttpServerService serverService,
                                                                                                                  final Function<SidecarClient, List<SidecarClient.OperationResult<?>>> requestExecutions) {

        final AtomicReference<List<SidecarClient.OperationResult<?>>> responseRefs = new AtomicReference<>(new ArrayList<>());

        final AtomicBoolean finished = new AtomicBoolean(false);

        serverService.addListener(new Service.Listener() {
            @Override
            public void running() {

                try (SidecarClient client = new SidecarClient.Builder().withHostname(TEST_HOSTNAME).withPort(TEST_PORT).build(resourceConfig)) {
                    responseRefs.set(requestExecutions.apply(client));
                } finally {
                    finished.set(true);
                }

            }
        }, MoreExecutors.directExecutor());

        return Pair.of(responseRefs, finished);
    }
}
