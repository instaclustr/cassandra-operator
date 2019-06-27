package com.instaclustr.jmx;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public final class ObjectNames {
    private ObjectNames() {}

    public static ObjectName create(final String name) {
        try {
            return ObjectName.getInstance(name);

        } catch (final MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ObjectName format(final String nameFormat, final Object... args) {
        return create(String.format(nameFormat, args));
    }
}
