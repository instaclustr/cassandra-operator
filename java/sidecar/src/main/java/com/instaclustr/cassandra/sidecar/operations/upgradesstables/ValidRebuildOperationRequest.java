package com.instaclustr.cassandra.sidecar.operations.upgradesstables;


import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.validation.Constraint;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.instaclustr.cassandra.sidecar.operations.rebuild.RebuildOperationRequest;
import com.instaclustr.sidecar.operations.OperationRequestValidator;

@Target({TYPE, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {
        ValidRebuildOperationRequest.RebuildOperationRequestValidator.class,
})
public @interface ValidRebuildOperationRequest {

    String message() default "{com.instaclustr.cassandra.sidecar.operations.upgradesstables.ValidRebuildOperationRequest.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static final class RebuildOperationRequestValidator extends OperationRequestValidator<ValidRebuildOperationRequest, RebuildOperationRequest> {
        @Override
        public boolean isValid(RebuildOperationRequest value, ConstraintValidatorContext context) {

            context.disableDefaultConstraintViolation();

            if (value.keyspace == null && value.specificTokens != null && !value.specificTokens.isEmpty()) {
                context
                        .buildConstraintViolationWithTemplate("{com.instaclustr.cassandra.sidecar.operations.upgradesstables.ValidRebuildOperationRequest.keyspaceMissingForSpecificTokens}")
                        .addConstraintViolation();

                return false;
            }

            return true;
        }
    }
}
