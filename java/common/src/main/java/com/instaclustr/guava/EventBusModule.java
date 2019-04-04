package com.instaclustr.guava;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedElement;

public class EventBusModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(EventBusModule.class);

    private static final Matcher<AnnotatedElement> EVENT_BUS_SUBSCRIBER_MATCHER = Matchers.annotatedWith(EventBusSubscriber.class);

    private final EventBus eventBus = new EventBus();

    public EventBusModule() {
        eventBus.register(new Object() {
            @Subscribe
            void handleDeadEvent(final DeadEvent event) {
                logger.trace("{} was posted to the bus and nobody cared.", event.getEvent());
            }
        });
    }

    @Override
    protected void configure() {
        bind(EventBus.class).toInstance(eventBus);

        // register all provisioned EventBusSubscribers with the EventBus
        bindListener(new AbstractMatcher<Binding<?>>() {
            @Override
            public boolean matches(final Binding<?> binding) {
                return EVENT_BUS_SUBSCRIBER_MATCHER.matches(binding.getKey().getTypeLiteral().getRawType());

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
