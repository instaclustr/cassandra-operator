package com.instaclustr.jersey.hk2;

import com.google.inject.Injector;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.InjectionManagerProvider;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;

import javax.inject.Inject;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

public class GuiceHK2BridgeFeature implements Feature {
    private final Injector injector;

    @Inject
    public GuiceHK2BridgeFeature(final Injector injector) {
        this.injector = injector;
    }

    @Override
    public boolean configure(final FeatureContext context) {

        final ServiceLocator serviceLocator = InjectionManagerProvider.getInjectionManager(context).getInstance(ServiceLocator.class);

        GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);

        serviceLocator.getService(GuiceIntoHK2Bridge.class).bridgeGuiceInjector(injector);

        return true;
    }
}
