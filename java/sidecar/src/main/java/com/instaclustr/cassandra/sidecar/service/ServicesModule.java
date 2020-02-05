package com.instaclustr.cassandra.sidecar.service;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jmx.org.apache.cassandra.service.CassandraJMXService;

public class ServicesModule extends AbstractModule {

    @Provides
    @Singleton
    public CassandraStatusService getCassandraStatusService(final CassandraJMXService cassandraJMXService) {
        return new CassandraStatusService(cassandraJMXService);
    }
}
