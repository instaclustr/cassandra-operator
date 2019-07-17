package com.instaclustr.picocli.typeconverter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

public class MeasureConverter {

    static final Pattern MEASURE_PATTERN = Pattern.compile("((?<value>.*\\d)(?<unit>.+))");

    protected Pair<String, String> getValueAndUnit(String parameter) {
        final String rawValue, rawUnit;
        {
            final List<String> tokens = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(parameter);

            if (tokens.size() == 1) {
                final String token = tokens.get(0);

                final Matcher matcher = MEASURE_PATTERN.matcher(token);
                if (!matcher.matches())
                    throw new CommandLine.TypeConversionException(token);

                rawValue = matcher.group("value");
                rawUnit = matcher.group("unit");

            } else {
                rawValue = tokens.get(0);
                rawUnit = tokens.get(1);
            }
        }

        return Pair.of(rawValue, rawUnit);
    }

    protected <V> V valueOf(final String s, final Class<V> valueType) throws NumberFormatException {
        if (valueType == Integer.class) {
            return (V) Integer.valueOf(s);

        } else if (valueType == Long.class) {
            return (V) Long.valueOf(s);

        } else if (valueType == Float.class) {
            return (V) Float.valueOf(s);

        }

        throw new UnsupportedOperationException("Conversion for type " + valueType.getCanonicalName() + " is unsupported.");
    }

    protected <E extends Enum<E>> Set<E> enumValuesStartingWith(final String token, final Class<E> enumClass) {
        final EnumSet<E> set = EnumSet.noneOf(enumClass);

        for (final E value : enumClass.getEnumConstants()) {
            if (value.name().toLowerCase().startsWith(token.toLowerCase()))
                set.add(value);
        }

        return set;
    }
}
