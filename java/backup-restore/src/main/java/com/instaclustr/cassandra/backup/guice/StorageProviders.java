package com.instaclustr.cassandra.backup.guice;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

@Target({TYPE, PARAMETER})
@Retention(RUNTIME)
@BindingAnnotation
public @interface StorageProviders {
}
