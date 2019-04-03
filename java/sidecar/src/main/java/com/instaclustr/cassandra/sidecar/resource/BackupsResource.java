package com.instaclustr.cassandra.sidecar.resource;

import com.instaclustr.backup.BackupArguments;
import com.instaclustr.cassandra.sidecar.model.BackupResponse;
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

@Path("/backups")
@Produces(MediaType.APPLICATION_JSON)
public class BackupsResource {
    private static final Logger logger = LoggerFactory.getLogger(BackupsResource.class);

    private final BackupService backupService;

    @Inject
    public BackupsResource(final BackupService backupService) {
        this.backupService = backupService;
    }


    @POST
    public BackupResponse createBackup(BackupArguments backupArguments) throws URISyntaxException, StorageException, ConfigurationException, IOException {
        logger.info("received backup request for {}", backupArguments.backupId);
        backupService.enqueueBackup(backupArguments);
        logger.info("enqueued backup request for {}", backupArguments.backupId);
        return new BackupResponse("success");
    }
}
