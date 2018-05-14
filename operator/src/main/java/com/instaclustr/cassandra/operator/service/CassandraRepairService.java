package com.instaclustr.cassandra.operator.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.instaclustr.cassandra.operator.model.DataCenter;
import com.instaclustr.cassandra.operator.model.key.DataCenterKey;

import io.kubernetes.client.apis.CoreV1Api;

public class CassandraRepairService extends AbstractScheduledService {
	static final Logger logger = LoggerFactory.getLogger(CassandraRepairService.class);
	
    private final String namespace = "default";

    private final CoreV1Api coreApi;
    private final Cache<DataCenterKey, DataCenter> dataCenterCache;
    private final ControllerService controllerService;
	
    @Inject
    public CassandraRepairService(final CoreV1Api coreApi,  final Cache<DataCenterKey, DataCenter> dataCenterCache, final ControllerService controllerService) {
        this.coreApi = coreApi;
        this.dataCenterCache = dataCenterCache;
        this.controllerService = controllerService;
    }
    
	@Override
	protected void runOneIteration() throws Exception {
		logger.debug("Repairing cassandra resources if missing...");

        for (final Map.Entry<DataCenterKey, DataCenter> cacheEntry : dataCenterCache.asMap().entrySet()) {
        	controllerService.reconcileDataCenter(cacheEntry.getKey());
        }
	}

	@Override
	protected Scheduler scheduler() {
		return Scheduler.newFixedDelaySchedule(0, 1, TimeUnit.MINUTES);
	}

}
