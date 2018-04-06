package com.instaclustr.cassandra.operator.service;

import com.google.common.util.concurrent.AbstractScheduledService;

import java.util.concurrent.TimeUnit;

public class GarbageCollectorService extends AbstractScheduledService {
    @Override
    protected void runOneIteration() throws Exception {

    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.MINUTES);
    }
}
