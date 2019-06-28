package com.instaclustr.cassandra.backup.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Messages;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class MeasureOptionHandler<V extends Number, U extends Enum<U>> extends OptionHandler<Measure<V, U>> {
    static final Pattern MEASURE_PATTERN = Pattern.compile("((?<value>.*\\d)(?<unit>.+))");

    private final Class<V> valueType;
    private final Class<U> unitType;
    private final Constructor<Measure<V, U>> constructor;

    public MeasureOptionHandler(final CmdLineParser parser, final OptionDef option, final Setter<Measure<V, U>> setter) {
        super(parser, option, setter);

        final Class<Measure<V, U>> fieldType = setter.getType();

        {
            final Type[] actualTypeArguments = ((ParameterizedType) fieldType.getGenericSuperclass()).getActualTypeArguments();
            valueType = (Class<V>) actualTypeArguments[0];
            unitType = (Class<U>) actualTypeArguments[1];
        }

        try {
            constructor = fieldType.getConstructor(valueType, unitType);

        } catch (final NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }

    static <E extends Enum<E>> Set<E> enumValuesStartingWith(final String token, final Class<E> enumClass) {
        final EnumSet<E> set = EnumSet.noneOf(enumClass);

        for (final E value : enumClass.getEnumConstants()) {
            if (value.name().toLowerCase().startsWith(token.toLowerCase()))
                set.add(value);
        }

        return set;
    }

    @SuppressWarnings("unchecked")
    private V valueOf(final String s) throws NumberFormatException {
        if (valueType == Integer.class) {
            return (V) Integer.valueOf(s);

        } else if (valueType == Long.class) {
            return (V) Long.valueOf(s);

        } else if (valueType == Float.class) {
            return (V) Float.valueOf(s);

        }

        throw new UnsupportedOperationException("Conversion for type " + valueType.getCanonicalName() + " is unsupported.");
    }

    @Override
    public int parseArguments(final Parameters params) throws CmdLineException {
        final String parameter = params.getParameter(0);

        final String rawValue, rawUnit;
        {
            final List<String> tokens = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(parameter);

            if (tokens.size() == 1) {
                final String token = tokens.get(0);

                final Matcher matcher = MEASURE_PATTERN.matcher(token);
                if (!matcher.matches())
                    throw new CmdLineException(owner, Messages.ILLEGAL_OPERAND, params.getParameter(-1), token);

                rawValue = matcher.group("value");
                rawUnit = matcher.group("unit");

            } else {
                rawValue = tokens.get(0);
                rawUnit = tokens.get(1);
            }
        }


        final V value;
        try {
            value = valueOf(rawValue);

        } catch (final NumberFormatException e) {
            throw new CmdLineException(owner, Messages.ILLEGAL_OPERAND.format(params.getParameter(-1), rawValue), e);
        }

        final U unit;
        try {
            unit = Iterables.getOnlyElement(enumValuesStartingWith(rawUnit, unitType));

        } catch (final NoSuchElementException e) {
            throw new CmdLineException(owner, Messages.ILLEGAL_OPERAND.format(params.getParameter(-1), rawUnit), e);

        } catch (final IllegalArgumentException e) {
            throw new CmdLineException(owner, String.format("more than one unit matches \"%s\" for \"%s\"", params.getParameter(-1), rawUnit), e);
        }

        try {
            final Measure<V, U> o = constructor.newInstance(value, unit);
            this.setter.addValue(o);

        } catch (final InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw Throwables.propagate(e);
        }

        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "measure";
    }
}