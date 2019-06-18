package com.instaclustr.sidecar.operations;

public interface OperationFactory<RequestT extends OperationRequest> {
    Operation createOperation(final RequestT request);

    //OperationRequestValidator<?, ?> createValidator();
}
