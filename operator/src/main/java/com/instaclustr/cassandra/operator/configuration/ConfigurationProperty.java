package com.instaclustr.cassandra.operator.configuration;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface ConfigurationProperty {
    String value();
}
