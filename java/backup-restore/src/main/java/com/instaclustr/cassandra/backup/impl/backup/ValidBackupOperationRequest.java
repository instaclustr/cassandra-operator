package com.instaclustr.cassandra.backup.impl.backup;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Files;

@Target({TYPE, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {
        ValidBackupOperationRequest.BackupOperationRequestValidator.class,
})
public @interface ValidBackupOperationRequest {

    String message() default "{com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequestValidator.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class BackupOperationRequestValidator implements ConstraintValidator<ValidBackupOperationRequest, BackupOperationRequest> {

        @Override
        public boolean isValid(final BackupOperationRequest value, final ConstraintValidatorContext context) {

            context.disableDefaultConstraintViolation();

            if (value.columnFamily != null && (value.keyspaces == null || value.keyspaces.size() != 1)) {
                context
                        .buildConstraintViolationWithTemplate("{com.instaclustr.cassandra.backup.impl.backup.BackupOperationRequestValidator.oneKeyspaceForColumnFamily}")
                        .addConstraintViolation();
                return false;
            }

            if (!Files.exists(value.sharedContainerPath)) {
                context.buildConstraintViolationWithTemplate(String.format("sharedContainerPath %s does not exist", value.sharedContainerPath)).addConstraintViolation();
                return false;
            }

            if (!Files.exists(value.cassandraDirectory)) {
                context.buildConstraintViolationWithTemplate(String.format("cassandraDirectory %s does not exist", value.cassandraDirectory)).addConstraintViolation();
                return false;
            }

            return true;
        }
    }
}
