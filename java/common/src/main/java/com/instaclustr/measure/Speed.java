package com.instaclustr.measure;

public enum Speed {
    SLOW(new DataRate(1L, DataRate.DataRateUnit.MBPS), 1),
    FAST(new DataRate(10L, DataRate.DataRateUnit.MBPS), 1),
    LUDICROUS(new DataRate(10L, DataRate.DataRateUnit.MBPS), 10),
    PLAID(null, 100);


    Speed(final DataRate bandwidth, final int concurrentUploads) {
        this.bandwidth = bandwidth;
        this.concurrentUploads = concurrentUploads;
    }

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }

    public final DataRate bandwidth;
    public final int concurrentUploads;
}
