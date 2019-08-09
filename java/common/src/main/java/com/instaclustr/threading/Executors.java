package com.instaclustr.threading;

import java.util.concurrent.ExecutorService;

import com.google.common.util.concurrent.MoreExecutors;

public abstract class Executors {

    private static final int DEFAULT_CONCURRENT_CONNECTIONS = 10;

    public static final class FixedTasksExecutor extends ExecutorServiceSupplier {
        @Override
        public ExecutorService get(final Integer concurrentTasks) {
            if (concurrentTasks != null) {
                return MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newFixedThreadPool(concurrentTasks));
            } else {
                return MoreExecutors.listeningDecorator(java.util.concurrent.Executors.newFixedThreadPool(DEFAULT_CONCURRENT_CONNECTIONS));
            }
        }
    }

    public static abstract class ExecutorServiceSupplier {

        public ExecutorService get() {
            return get(DEFAULT_CONCURRENT_CONNECTIONS);
        }

        public abstract ExecutorService get(final Integer concurrentTasks);
    }
}
