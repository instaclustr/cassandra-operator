package com.instaclustr.operations;

@FunctionalInterface
public interface OperationFactory<RequestT extends OperationRequest> {
    Operation createOperation(final RequestT request);
}
