package com.instaclustr.cassandra.operator.configuration;

import com.google.common.base.MoreObjects;

import java.lang.annotation.Annotation;

class ConfigurationPropertyImpl implements ConfigurationProperty {
    private final String value;

    ConfigurationPropertyImpl(final String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    public int hashCode() {
        // This is specified in java.lang.Annotation.
        return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ConfigurationProperty)) {
            return false;
        }

        final ConfigurationProperty other = (ConfigurationProperty) obj;
        return value.equals(other.value());
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return ConfigurationProperty.class;
    }

    public String toString() {
        return "@" + MoreObjects.toStringHelper(this).add("value", value).toString();
    }
}
