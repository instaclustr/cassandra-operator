package com.instaclustr.measure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * A value and its associated unit. e.g. 1 metre, 10 hours, 20
 * @param <V> the value type
 * @param <U> the unit type
 */
public abstract class Measure<V extends Number, U extends Enum<U>> {
    public final V value;
    public final U unit;

    @JsonCreator
    public Measure(@JsonProperty("value") final V value, @JsonProperty("unit") final U unit) {
        this.value = value;
        this.unit = unit;
    }

    @Override
    public String toString() {
        return String.format("%s %s", value, unit);
    }
}
