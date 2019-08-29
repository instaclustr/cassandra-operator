package com.instaclustr.operations;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class OperationsExpirationService extends AbstractScheduledService {
    private final long expirationPeriodInSeconds;
    private final Map<UUID, Operation> operations;

    @Inject
    public OperationsExpirationService(final @OperationsMap Map<UUID, Operation> operations,
                                       final @Named("operationsExpirationPeriod") long expirationPeriodInSeconds) {
        this.operations = operations;
        this.expirationPeriodInSeconds = expirationPeriodInSeconds;
    }

    @Override
    protected void runOneIteration() throws Exception {
        final Instant expirationThreshold = Instant.now().minusSeconds(expirationPeriodInSeconds);
        operations.values().removeIf(value -> value.state.isTerminalState() && value.completionTime != null && value.completionTime.isBefore(expirationThreshold));
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(expirationPeriodInSeconds, expirationPeriodInSeconds, TimeUnit.SECONDS);
    }
}
