package com.instaclustr.operations;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import javax.validation.Validation;
import javax.validation.Validator;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.instaclustr.cassandra.sidecar.Sidecar;
import com.instaclustr.operations.SidecarClient.OperationResult;
import com.instaclustr.sidecar.http.JerseyHttpServerModule;
import com.instaclustr.sidecar.http.JerseyHttpServerService;
import com.instaclustr.threading.ExecutorsModule;
import jmx.org.apache.cassandra.service.StorageServiceMBean;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.ResourceConfig;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class AbstractSidecarTest {

    @Inject
    ResourceConfig resourceConfig;

    @Inject
    ObjectMapper objectMapper;

    JerseyHttpServerService serverService;

    SidecarClient sidecarClient;

    Validator validator;

    protected List<Module> getModules() {
        return ImmutableList.of();
    }

    @BeforeMethod
    public void setup() {

        List<Module> modules = new ArrayList<Module>() {{
            add(new OperationsModule(3600));
            add(new AbstractModule() {
                @Override
                protected void configure() {

                    StorageServiceMBean mock = Mockito.mock(StorageServiceMBean.class);

                    doNothing().when(mock).rebuild(any(), any(), any(), any());

                    try {
                        when(mock.upgradeSSTables(anyString(), anyBoolean(), anyInt(), anyVararg())).thenReturn(0);
                    } catch (Exception ex) {
                        // intentionally empty
                    }

                    try {
                        when(mock.scrub(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyString(), anyVararg())).thenReturn(0);
                    } catch (final Exception ex) {
                        throw new RuntimeException(ex);
                    }

                    try {
                        doNothing().when(mock).decommission();
                    } catch (InterruptedException e) {
                        // intentionally empty
                    }

                    try {
                        when(mock.forceKeyspaceCleanup(anyInt(), anyString(), anyVararg())).thenReturn(0);
                    } catch (Exception ex) {
                        // intentionally empty
                    }

                    bind(StorageServiceMBean.class).toInstance(Mockito.mock(StorageServiceMBean.class));
                }
            });
            add(new JerseyHttpServerModule());
            add(new ExecutorsModule());
            addAll(Sidecar.operationModules());
        }};

        modules.addAll(getModules());

        final Injector injector = Guice.createInjector(modules);

        injector.injectMembers(this);

        // Port 0 will be a randomly assigned ephemeral port by the OS - each concrete class will get it's own port
        // Note we won't know the address till a socket actually binds on it, so we can't use this again, which is why
        // we call getServerInetAddress from the serverService rather than using the passed in InetSocketAddress

        serverService = new JerseyHttpServerService(new InetSocketAddress("localhost", 0), resourceConfig);

        sidecarClient = new SidecarClient.Builder().withHostname(serverService.getServerInetAddress().getHostName()).withPort(serverService.getServerInetAddress().getPort()).build(resourceConfig);

        validator = Validation.byDefaultProvider().configure().buildValidatorFactory().getValidator();
    }

    @AfterMethod
    public void teardown() {
        sidecarClient.close();
        serverService.stopAsync().awaitTerminated();
    }

    protected Pair<AtomicReference<List<OperationResult<?>>>, AtomicBoolean> performOnRunningServer(final Function<SidecarClient, List<OperationResult<?>>> requestExecutions) {

        final AtomicReference<List<OperationResult<?>>> responseRefs = new AtomicReference<>(new ArrayList<>());

        final AtomicBoolean finished = new AtomicBoolean(false);

        serverService.addListener(new Service.Listener() {
            @Override
            public void running() {
                try {
                    responseRefs.set(requestExecutions.apply(sidecarClient));
                } finally {
                    finished.set(true);
                }

            }
        }, MoreExecutors.directExecutor());

        serverService.startAsync();

        return Pair.of(responseRefs, finished);
    }
}
