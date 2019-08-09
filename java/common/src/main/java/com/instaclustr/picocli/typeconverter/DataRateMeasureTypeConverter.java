package com.instaclustr.picocli.typeconverter;

import com.google.common.collect.Iterables;
import com.instaclustr.measure.DataRate;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

public class DataRateMeasureTypeConverter extends MeasureConverter implements CommandLine.ITypeConverter<DataRate> {
    @Override
    public DataRate convert(final String value) throws Exception {
        final Pair<String, String> valueAndUnit = getValueAndUnit(value);

        final Long longValue;

        try {
            longValue = valueOf(valueAndUnit.getLeft(), Long.class);
        } catch (final NumberFormatException ex) {
            throw new CommandLine.TypeConversionException(String.format("%s is not valid Long", valueAndUnit.getLeft()));
        }

        final DataRate.DataRateUnit dataRateUnit;

        try {
            dataRateUnit = Iterables.getOnlyElement(enumValuesStartingWith(valueAndUnit.getRight(), DataRate.DataRateUnit.class));
        } catch (final Exception ex) {
            throw new CommandLine.TypeConversionException(String.format("%s is not valid DataRateUnit", valueAndUnit.getLeft()));
        }

        return new DataRate(longValue, dataRateUnit);
    }
}
