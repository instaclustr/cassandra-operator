package com.instaclustr.cassandra.operator.configuration;

import com.google.common.base.StandardSystemProperty;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface SystemProperty {
    public StandardSystemProperty value();
}
