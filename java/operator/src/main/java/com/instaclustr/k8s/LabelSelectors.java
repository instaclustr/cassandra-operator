package com.instaclustr.k8s;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class LabelSelectors {

    public static String equalitySelector(final String label, final String value) {
        return equalitySelector(ImmutableMap.of(label, value));
    }

    public static String equalitySelector(final Map<String, String> labelsAndValues) {
        return Joiner.on(',').withKeyValueSeparator('=').join(labelsAndValues);
    }


    private LabelSelectors() {}
}
