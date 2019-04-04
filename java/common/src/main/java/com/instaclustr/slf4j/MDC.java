package com.instaclustr.slf4j;

import java.io.Closeable;

public class MDC {
    public static class MDCCloseable implements Closeable {
        private final String key;
        private final String previousValue;
        private final MDCCloseable next;

        MDCCloseable(final String key, final String value) {
            this(key, value, null);
        }

        MDCCloseable(final String key, final String value, final MDCCloseable next) {
            this.key = key;
            this.previousValue = org.slf4j.MDC.get(key);
            this.next = next;

            org.slf4j.MDC.put(key, value);
        }

        @Override
        public void close() {
            if (previousValue != null) {
                org.slf4j.MDC.put(key, previousValue);
            } else {
                org.slf4j.MDC.remove(key);
            }

            if (next != null) {
                next.close();
            }
        }

        public MDCCloseable andPut(final String key, final String value) {
            return new MDCCloseable(key, value, this);
        }
    }

    public static MDCCloseable put(final String key, final String value) {
        return new MDCCloseable(key, value);
    }
}
