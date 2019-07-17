package com.instaclustr.measure;

import java.util.concurrent.TimeUnit;

public class Time extends Measure<Long, TimeUnit> {
    public Time(final Long value, final TimeUnit unit) {
        super(value, unit);
    }

    public Time asSeconds() {
        return new Time(unit.toSeconds(value), TimeUnit.SECONDS);
    }
}
