package com.instaclustr.picocli.typeconverter;

import java.util.concurrent.TimeUnit;

import com.google.common.collect.Iterables;
import com.instaclustr.measure.Time;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

public class TimeMeasureTypeConverter extends MeasureConverter implements CommandLine.ITypeConverter<Time> {
    @Override
    public Time convert(final String value) throws Exception {

        final Pair<String, String> valueAndUnit = getValueAndUnit(value);

        final Long timeValue;

        try {
            timeValue = valueOf(valueAndUnit.getLeft(), Long.class);
        } catch (final NumberFormatException ex) {
            throw new CommandLine.TypeConversionException(String.format("%s is not valid Long", valueAndUnit.getLeft()));
        }

        final TimeUnit timeUnitValue;

        try {
            timeUnitValue = Iterables.getOnlyElement(enumValuesStartingWith(valueAndUnit.getRight(), TimeUnit.class));
        } catch (final Exception ex) {
            throw new CommandLine.TypeConversionException(String.format("%s is not valid TimeUnit", valueAndUnit.getLeft()));
        }

        return new Time(timeValue, timeUnitValue);
    }
}
