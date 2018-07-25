package com.instaclustr.cassandra.operator.model.key;

import com.instaclustr.cassandra.operator.model.Backup;
import io.kubernetes.client.models.V1ObjectMeta;

public class BackupKey extends Key<Backup> {
    public BackupKey(String name, String namespace) { super(name, namespace); }

    public static BackupKey forBackup(final Backup Backup) {
        final V1ObjectMeta metadata = Backup.getMetadata();

        return new BackupKey(metadata.getNamespace(), metadata.getName());
    }
}
