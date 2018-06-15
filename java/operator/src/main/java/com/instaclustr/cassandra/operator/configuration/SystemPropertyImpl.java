package com.instaclustr.cassandra.operator.configuration;

import com.google.common.base.MoreObjects;
import com.google.common.base.StandardSystemProperty;

import java.lang.annotation.Annotation;

public class SystemPropertyImpl implements SystemProperty {
    private final StandardSystemProperty value;

    SystemPropertyImpl(final StandardSystemProperty value) {
        this.value = value;
    }

    @Override
    public StandardSystemProperty value() {
        return value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return SystemProperty.class;
    }

    public int hashCode() {
        // This is specified in java.lang.Annotation.
        return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof SystemProperty)) {
            return false;
        }

        final SystemProperty other = (SystemProperty) obj;
        return value.equals(other.value());
    }

    public String toString() {
        return "@" + MoreObjects.toStringHelper(this).add("value", value.name()).toString();
    }
}
