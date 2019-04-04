package com.instaclustr.cassandra.operator.service;

import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.backup.CommonBackupArguments;
import com.instaclustr.backup.StorageProvider;
import com.instaclustr.cassandra.operator.configuration.BackupConfiguration;
import com.instaclustr.cassandra.operator.event.BackupWatchEvent;
import com.instaclustr.cassandra.operator.k8s.K8sResourceUtils;
import com.instaclustr.cassandra.operator.k8s.OperatorLabels;
import com.instaclustr.cassandra.operator.model.Backup;
import com.instaclustr.cassandra.operator.model.BackupSpec;
import com.instaclustr.cassandra.operator.model.key.BackupKey;
import com.instaclustr.cassandra.sidecar.model.BackupResponse;
import com.instaclustr.guava.EventBusSubscriber;
import com.instaclustr.k8s.watch.ResourceCache;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@EventBusSubscriber
public class BackupControllerService extends AbstractExecutionThreadService {
    private static final Logger logger = LoggerFactory.getLogger(BackupControllerService.class);
    private static final BackupKey POISON = new BackupKey(null, null);

    private final K8sResourceUtils k8sResourceUtils;
    private final ResourceCache<BackupKey, Backup> backupCache;
    private Client client;
    private final CustomObjectsApi customObjectsApi;

    private final BlockingQueue<BackupKey> backupQueue = new LinkedBlockingQueue<>();

    @Inject
    public BackupControllerService(final K8sResourceUtils k8sResourceUtils,
                                   final ResourceCache<BackupKey, Backup> backupCache,
                                   final CustomObjectsApi customObjectsApi) {

        this.k8sResourceUtils = k8sResourceUtils;
        this.backupCache = backupCache;
        this.customObjectsApi = customObjectsApi;
        client = ClientBuilder.newClient();

    }

    @Subscribe
    void handleBackupEvent(final BackupWatchEvent event) {
        logger.info("Received BackupWatchEvent {}.", event);
        if(!Strings.isNullOrEmpty(event.backup.getSpec().getStatus()) && !event.backup.getSpec().getStatus().equals("PROCESSED")) {
            backupQueue.add(BackupKey.forBackup(event.backup));
        } else {
            logger.debug("Skipping already processed backup {}", event.backup.getMetadata().getName());
        }
    }

    @Override
    protected void run() throws Exception {
        while (true) {
            final BackupKey backupKey = backupQueue.take();
            if (backupKey == POISON)
                return;

            try (@SuppressWarnings("unused") final MDC.MDCCloseable _backupName = MDC.putCloseable("Backup", backupKey.name);
                 @SuppressWarnings("unused") final MDC.MDCCloseable _backupNamespace = MDC.putCloseable("Namespace", backupKey.namespace)) {

                final Backup backup = backupCache.get(backupKey);

                // backup deleted
                if (backup == null) {
                    logger.info("Deleting Backup.", backupKey);
                    deleteBackup(backupKey);

                    continue;
                }

                // backup created or modified
                try {
                    logger.info("Reconciling Backup.");
                    createOrReplaceBackup(backup);

                } catch (final Exception e) {
                    logger.warn("Failed to reconcile Backup. This will be an exception in the future.", e);
                }
            }
        }
    }

    private boolean callBackupApi(final V1Pod pod, Backup backup) {
        // TODO: move this to SidecarClient

        try {
            WebTarget target = client.target("http://" + pod.getStatus().getPodIP() + ":4567").path("backups");
            BackupArguments backupArguments = BackupConfiguration.generateBackupArguments(pod.getStatus().getPodIP(),
                    7199,
                    backup.getMetadata().getName(),
                    StorageProvider.valueOf(backup.getSpec().getBackupType()),
                    backup.getSpec().getTarget(),
                    backup.getMetadata().getLabels().get(OperatorLabels.DATACENTER));

            backupArguments.backupId = pod.getSpec().getHostname();
            backupArguments.speed = CommonBackupArguments.Speed.LUDICROUS;

            BackupResponse response = target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(backupArguments, MediaType.APPLICATION_JSON_TYPE), BackupResponse.class);
            return response.getStatus().equals("success");

        } catch (WebApplicationException e) {
            logger.warn("bad request", e);
            return false;
        }
    }


    private void createOrReplaceBackup(final Backup backup) throws ApiException, UnknownHostException {
        // TODO: use a different field as a selector for the DC to backup
        final String dataCenterPodsLabelSelector = backup.getMetadata().getLabels().entrySet().stream()
                .map(x -> x.getKey() + "=" + x.getValue())
                .collect(Collectors.joining(","));

        final BackupSpec backupSpec = backup.getSpec();

        final List<V1Pod> pods = k8sResourceUtils.listNamespacedPods(backup.getMetadata().getNamespace(), null, dataCenterPodsLabelSelector);

        final boolean anyFailed = pods.parallelStream()
                .map(pod -> callBackupApi(pod, backup))
                .anyMatch(result -> result == false);

        // TODO: don't modify .spec. Use .status instead.
        backupSpec.setStatus(anyFailed ? "FAILED" : "PROCESSED");
        customObjectsApi.patchNamespacedCustomObject("stable.instaclustr.com", "v1", backup.getMetadata().getNamespace(), "cassandra-backups", backup.getMetadata().getName(), backup);
    }

    private void deleteBackup(BackupKey backupKey) {
        logger.warn("Deleting backups is not implemented.");
    }

    @Override
    protected void triggerShutdown() {
        backupQueue.add(POISON);
    }
}
