package com.instaclustr.cassandra.sidecar.resource;

import com.google.common.collect.ImmutableList;
import com.instaclustr.cassandra.sidecar.service.backup.Backup;
import com.instaclustr.cassandra.sidecar.service.backup.BackupService;
import com.microsoft.azure.storage.StorageException;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Path("/backups")
public class BackupsResource {

    private final BackupService backupService;

    @Inject
    public BackupsResource(final BackupService backupService) {
        this.backupService = backupService;
    }

    @GET
    public List<Backup> listBackups() {
        return ImmutableList.of();
    }

    @POST
    public Response createBackup() throws URISyntaxException, StorageException, ConfigurationException, IOException {
        backupService.startBackup();

        return Response.created(null).build();
    }


    @Path("{backup}")
    BackupResource subBackup() {
        return null;
    }


    static class BackupResource {
    }

}
