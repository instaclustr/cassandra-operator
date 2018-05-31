package com.instaclustr.cassandra.operator.preflight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.Callable;

public final class Preflight implements Callable<Void> {
    static final Logger logger = LoggerFactory.getLogger(Preflight.class);

    private final Set<Operation> operations;

    @Inject
    public Preflight(final Set<Operation> operations) {
        this.operations = operations;
    }

    @Override
    public Void call() throws Exception {
        logger.debug("Preflight operations to run: [{}]", operations);

        for (final Operation operation : operations) {
            logger.debug("Running preflight operation {}.", operation);

            try {
                operation.run();

            } catch (final Exception e) {
                logger.error("Preflight operation {} failed to execute.", operation);

                throw e;
            }
        }

        return null;
    }
}
