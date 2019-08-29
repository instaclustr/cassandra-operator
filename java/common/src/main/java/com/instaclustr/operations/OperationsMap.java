package com.instaclustr.operations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/**
 * Annotation put on Map of UUIDs and Operations where operations are put
 * upon submission to OperationsService and removed after expiration from OperationsExpirationService
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, METHOD})
@BindingAnnotation
public @interface OperationsMap {
}
