package com.instaclustr.cassandra.operator.service;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.CommonBackupArguments;
import com.instaclustr.backup.StorageProvider;
import com.instaclustr.cassandra.operator.configuration.BackupConfiguration;
import com.instaclustr.cassandra.operator.event.BackupWatchEvent;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.key.BackupKey;
import com.instaclustr.cassandra.sidecar.model.BackupResponse;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class BackupControllerService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(BackupControllerService.class);
    private static final BackupKey POISON = new BackupKey(null, null);
    private final K8sResourceUtils k8sResourceUtils;
    private final CoreV1Api coreApi;
    private final Map<BackupKey, Backup> backupCache;
    private Client client;


    private final BlockingQueue<BackupKey> backupQueue = new LinkedBlockingQueue<>();

    @Inject
    public BackupControllerService(final K8sResourceUtils k8sResourceUtils,
                                   final CoreV1Api coreApi,
                                   final Map<BackupKey, Backup> backupCache) {

        this.k8sResourceUtils = k8sResourceUtils;
        this.coreApi = coreApi;
        this.backupCache = backupCache;
        client = ClientBuilder.newClient();

    }

    @Subscribe
    void handleBackupEvent(final BackupWatchEvent event) {
        logger.info("Received BackupWatchEvent {}.", event);
        backupQueue.add(BackupKey.forBackup(event.backup));
    }

    @Override
    protected void run() throws Exception {
        while(isRunning()) {
            final BackupKey backupKey = backupQueue.take();
            if(backupKey == POISON)
                return;

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _dataCenterName = MDC.putCloseable("Backup", backupKey.name);
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

    private boolean callBackupApi(final V1Pod pod, Backup backup) {
        try {
            WebTarget target = client.target("http://" + pod.getStatus().getPodIP() + ":4567").path("backups");
            BackupArguments backupArguments = BackupConfiguration.generateBackupArguments(pod.getStatus().getPodIP(),
                    7199,
                    backup.getMetadata().getName(),
                    StorageProvider.valueOf(backup.getSpec().getBackupType()),
                    backup.getSpec().getTarget(),
                    backup.getMetadata().getLabels().get("cassandra-datacenter"));

            backupArguments.backupId = pod.getSpec().getHostname();
            backupArguments.speed = CommonBackupArguments.Speed.LUDICROUS;

            BackupResponse response = target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(backupArguments, MediaType.APPLICATION_JSON_TYPE), BackupResponse.class);
            return response.getStatus().equals("success");
        } catch (BadRequestException e) {
            logger.warn("bad request", e);
            return false;
        }
    }


    private void createOrReplaceBackup(final Backup backup) throws ApiException, UnknownHostException {
        String labels = backup.getMetadata().getLabels().entrySet().stream()
                .map(x -> x.getKey() + "=" + x.getValue())
                .collect(Collectors.joining(","));

        if(coreApi.listNamespacedPod(backup.getMetadata().getNamespace(), null, null,
                null, null, labels, null, null, null, null)
                .getItems()
                .parallelStream()
                .map(x -> callBackupApi(x, backup))
                .anyMatch(e -> !e)) {
            //TODO: Set backup status to PROCESSED
        }

    }

    private void deleteBackup(BackupKey backupKey) {

    }

    @Override
    protected void triggerShutdown() {
        backupQueue.add(POISON);
    }
}
