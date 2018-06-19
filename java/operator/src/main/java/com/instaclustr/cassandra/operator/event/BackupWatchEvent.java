package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import com.instaclustr.cassandra.operator.model.Backup;

import javax.inject.Inject;
import javax.annotation.Nullable;

public abstract class BackupWatchEvent extends WatchEvent {
    public Backup backup;
    
    
    protected BackupWatchEvent(final Backup backup) { this.backup = backup; }

    public interface Factory extends WatchEvent.Factory<Backup> {
        Added createAddedEvent(final Backup dataCenter);
        Modified createModifiedEvent(@Nullable @Assisted("old") final Backup oldBackup, @Assisted("new") final Backup newBackup);
        Deleted createDeletedEvent(final Backup dataCenter);
    }


    public static class Added extends BackupWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final Backup backup) {
            super(backup);
        }
    }

    public static class Modified extends BackupWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final Backup oldbackup;

        @Inject
        public Modified(@Nullable @Assisted("old") final Backup oldBackup, @Assisted("new") final Backup newBackup) {
            super(newBackup);
            this.oldbackup = oldBackup;
        }
    }

    public static class Deleted extends BackupWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final Backup dataCenter) {
            super(dataCenter);
        }
    }

    
    
}
