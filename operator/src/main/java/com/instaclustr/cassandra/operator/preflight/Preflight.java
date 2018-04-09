package com.instaclustr.cassandra.operator.preflight;

import javax.inject.Inject;
import java.util.Set;

public final class Preflight {

    private final Set<Operation> operations;

    @Inject
    public Preflight(final Set<Operation> operations) {
        this.operations = operations;
    }

    public void run() throws Exception {
        for (final Operation operation : operations) {
            operation.run();
        }
    }
}
