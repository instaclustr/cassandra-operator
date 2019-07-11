package com.instaclustr.sidecar.operations;

import javax.validation.ConstraintValidator;
import java.lang.annotation.Annotation;

public abstract class OperationRequestValidator<T extends Annotation, U extends OperationRequest> implements ConstraintValidator<T, U> {
}
