package com.instaclustr.cassandra.sidecar.operation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.instaclustr.cassandra.sidecar.model.operation.OperationState;
import com.instaclustr.cassandra.sidecar.model.operation.Operation;
import com.instaclustr.cassandra.sidecar.model.result.OperationResult;
import jmx.org.apache.cassandra.service.StorageServiceMBean;

public abstract class OperationTask<O extends Operation, R extends OperationResult> implements Callable<R> {

    private final O operation;

    private final R result;

    private StopWatch stopWatch = new StopWatch();

    protected StorageServiceMBean storageServiceMBean;

    public OperationTask(final O operation, final R result) {
        this.operation = operation;
        this.result = result;
    }

    @Override
    public R call() {

        stopWatch.start();

        result.setOperationState(OperationState.RUNNING);
        result.setStart(stopWatch.getStart());
        result.setId(operation.getId());

        try {
            executeTask(operation, result);
            result.setOperationState(OperationState.FINISHED);
        } catch (Exception e) {
            result.setOperationState(OperationState.CRASHED);
            result.setException(e);
        }

        stopWatch.stop();

        result.setStop(stopWatch.getStop());
        result.setDuration(stopWatch.duration().get());

        return result;
    }

    protected abstract void executeTask(final O operation, final R result) throws Exception;

    public Operation getOperation() {
        return operation;
    }

    public R getOperationResult() {
        return result;
    }

    public void setStorageServiceMBean(StorageServiceMBean storageServiceMBean) {
        this.storageServiceMBean = storageServiceMBean;
    }

    public static class StopWatch {

        private Instant start;

        private Instant stop;

        private boolean started = false;

        public Instant getStart() {
            return start;
        }

        public Instant getStop() {
            return stop;
        }

        public void start() {
            if (!started) {
                start = Instant.now();

                started = true;
            }
        }

        public void stop() {
            if (started) {
                stop = Instant.now();
            }
        }

        public void reset() {
            start = null;
            stop = null;
            started = false;
        }

        public Optional<Duration> duration() {
            if (start == null || stop == null) {
                return Optional.empty();
            }

            return Optional.of(Duration.between(start, stop));
        }
    }
}
