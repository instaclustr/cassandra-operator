package com.instaclustr.cassandra.sidecar.resource;

import com.google.common.collect.ImmutableList;
import com.instaclustr.backup.BackupArguments;
import com.instaclustr.cassandra.sidecar.model.BackupResponse;
import com.instaclustr.cassandra.sidecar.service.backup.Backup;
import com.instaclustr.cassandra.sidecar.service.backup.BackupService;
import com.microsoft.azure.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Path("/backups")
public class BackupsResource {

    private final BackupService backupService;
    private static final Logger logger = LoggerFactory.getLogger(BackupsResource.class);


    @Inject
    public BackupsResource(final BackupService backupService) {
        this.backupService = backupService;
    }

    @GET
    public List<Backup> listBackups() {
        return ImmutableList.of();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public BackupResponse createBackup(BackupArguments backupArguments) throws URISyntaxException, StorageException, ConfigurationException, IOException {
        logger.info("received backup request for {}", backupArguments.backupId);
        boolean success = backupService.enqueueBackup(backupArguments);
        logger.info("enqueued backup request for {}", backupArguments.backupId);
        return new BackupResponse(success ? "success" : "backupAlreadyQueued");
    }


    @Path("{backup}")
    public BackupResource subBackup() {
        return null;
    }


    static class BackupResource {
    }

}
