package com.instaclustr.cassandra.operator.service;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.key.BackupKey;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class BackupControllerService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(ControllerService.class);
    private static final BackupKey POISON = new BackupKey(null, null);
    private final K8sResourceUtils k8sResourceUtils;
    private final CoreV1Api coreApi;
    private final Map<BackupKey, Backup> backupCache;

    private final BlockingQueue<BackupKey> backupQueue = new LinkedBlockingQueue<>();

    @Inject
    public BackupControllerService(final K8sResourceUtils k8sResourceUtils,
                                   final CoreV1Api coreApi,
                                   final Map<BackupKey, Backup> backupCache) {

        this.k8sResourceUtils = k8sResourceUtils;
        this.coreApi = coreApi;
        this.backupCache = backupCache;
    }

    @Override
    protected void run() throws Exception {
        while(isRunning()) {
            final BackupKey backupKey = backupQueue.take();
            if(backupKey == POISON)
                return;

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterName = MDC.putCloseable("DataCenter", backupKey.name);
                 @SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterNamespace = MDC.putCloseable("Namespace", backupKey.namespace)) {

                final Backup dataCenter = backupCache.get(backupKey);

                // data center deleted
                if (dataCenter == null) {
                    logger.info("Deleting Backup.", backupKey);
                    deleteBackup(backupKey);

                    continue;
                }

                // data center created or modified
                try {
                    logger.info("Reconciling Backup.");
                    createOrReplaceBackup(dataCenter);

                } catch (final Exception e) {
                    logger.warn("Failed to reconcile Backup. This will be an exception in the future.", e);
                }
            }
        }
    }

    private boolean callBackupApi(final String hostname) {
        boolean success = false;
        //TODO: Call api
        return success;
    }


    private void createOrReplaceBackup(final Backup backup) throws ApiException, UnknownHostException {
        String labels = backup.getMetadata().getLabels().entrySet().stream()
                .map(x -> x.getKey() + "=" + x.getValue())
                .collect(Collectors.joining(","));

        coreApi.listNamespacedPod(backup.getMetadata().getNamespace(), null, null,
                null, null, labels, null, null, null, null)
                .getItems()
                .stream()
                .map(V1Pod::getSpec)
                .map(V1PodSpec::getHostname)
                .parallel()
                .map(this::callBackupApi)
                .anyMatch(e -> !e);

    }

    private void deleteBackup(BackupKey backupKey) {

    }
}
