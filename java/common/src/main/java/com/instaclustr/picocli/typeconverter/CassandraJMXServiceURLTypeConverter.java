package com.instaclustr.picocli.typeconverter;

public final class CassandraJMXServiceURLTypeConverter extends JMXServiceURLTypeConverter {

    public static final int DEFAULT_CASSANDRA_JMX_PORT = 7199;

    @Override
    protected int defaultPort() {
        return DEFAULT_CASSANDRA_JMX_PORT;
    }
}
