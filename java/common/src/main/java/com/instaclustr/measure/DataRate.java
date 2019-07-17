package com.instaclustr.measure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DataRate extends Measure<Long, DataRate.DataRateUnit> {
    @JsonCreator
    public DataRate(@JsonProperty("value") final Long value, @JsonProperty("unit") final DataRateUnit unit) {
        super(value, unit);
    }

    public enum DataRateUnit {
        BPS("B/s", "Bytes per second") {
            @Override
            long toBytesPerSecond(final long value) {
                return value;
            }
        },
        KBPS("KB/s", "Kilobytes per second") {
            @Override
            long toBytesPerSecond(final long value) {
                return (value * 1000);
            }
        },
        MBPS("MB/s", "Megabytes per second") {
            @Override
            long toBytesPerSecond(final long value) {
                return KBPS.toBytesPerSecond(value * 1000);
            }
        },
        GBPS("GB/s", "Gigabytes per second") {
            @Override
            long toBytesPerSecond(final long value) {
                return MBPS.toBytesPerSecond(value * 1000);
            }
        };

        final String unit, description;

        @JsonCreator
        DataRateUnit(@JsonProperty("unit") final String unit, @JsonProperty("description") final String description) {
            this.unit = unit;
            this.description = description;
        }

        abstract long toBytesPerSecond(long value);

        @Override
        public String toString() {
            return unit;
        }
    }

    public DataRate asBytesPerSecond() {
        return new DataRate(unit.toBytesPerSecond(value), DataRateUnit.BPS);
    }

    public String toString() {
        DataRateUnit unit = this.unit;
        float b = value;

        while (b > 1000) {
            b /= 1000;
            unit = DataRateUnit.values()[unit.ordinal() + 1];

            if (unit.ordinal() + 1 == DataRateUnit.values().length)
                break;
        }

        return String.format("%2.2f %s", b, unit);
    }
}
