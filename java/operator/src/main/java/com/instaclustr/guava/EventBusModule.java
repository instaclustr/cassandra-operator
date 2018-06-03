package com.instaclustr.guava;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBusModule extends AbstractModule {
    static final Logger logger = LoggerFactory.getLogger(EventBusModule.class);

    private final EventBus eventBus = new EventBus();

    public EventBusModule() {
        eventBus.register(new Object() {
            @Subscribe
            void foo(final DeadEvent event) {
                logger.trace("{} was posted to the bus and nobody cared.", event.getEvent());
            }
        });
    }

    @Override
    protected void configure() {
        bind(EventBus.class).toInstance(eventBus);

        // register all provisioned Services with the EventBus
        bindListener(new AbstractMatcher<Binding<?>>() {
            @Override
            public boolean matches(final Binding<?> binding) {
                return Matchers.subclassesOf(Service.class)
                        .matches(binding.getKey().getTypeLiteral().getRawType());
            }
        }, new ProvisionListener() {
            @Override
            public <T> void onProvision(final ProvisionInvocation<T> provision) {
                final T object = provision.provision();

                eventBus.register(object);
            }
        });
    }
}
