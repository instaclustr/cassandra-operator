package com.instaclustr.measure;

public class DataSize {
    private enum DataSizeUnit {
        BYTES,
        KILOBYTES,
        MEGABYTES,
        GIGABYTES,
        TERABYTES,
        EXABYTES
    }

    public static String bytesToHumanReadable(final long bytes) {
        DataSizeUnit size = DataSizeUnit.BYTES;
        float b = bytes;

        while (b > 1000) {
            b /= 1000;
            size = DataSizeUnit.values()[size.ordinal() + 1];
        }

        return String.format("%2.2f %s", b, size.name().toLowerCase());
    }
}
