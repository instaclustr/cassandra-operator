package com.instaclustr.cassandra.operator.jmx;

import com.google.inject.AbstractModule;

public class CassandraModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraConnectionFactory.class).asEagerSingleton();
    }
}
