package com.instaclustr.cassandra.operator.event;

import com.google.inject.assistedinject.Assisted;
import io.kubernetes.client.models.V1Secret;

import javax.annotation.Nullable;
import javax.inject.Inject;

@SuppressWarnings("WeakerAccess")
public abstract class SecretWatchEvent extends WatchEvent {
    public final V1Secret secret;

    protected SecretWatchEvent(final V1Secret secret) {
        this.secret = secret;
    }

    public interface Factory extends WatchEvent.Factory<V1Secret> {
        Added createAddedEvent(final V1Secret secret);
        Modified createModifiedEvent(@Nullable @Assisted("old") final V1Secret secret, @Assisted("new") final V1Secret newSecret);
        Deleted createDeletedEvent(final V1Secret secret);
    }

    public static class Added extends SecretWatchEvent implements WatchEvent.Added {
        @Inject
        public Added(@Assisted final V1Secret secret) {
            super(secret);
        }
    }

    public static class Modified extends SecretWatchEvent implements WatchEvent.Modified {
        @Nullable
        public final V1Secret oldSecret;

        @Inject
        public Modified(@Nullable final V1Secret oldSecret, final V1Secret newSecret) {
            super(newSecret);
            this.oldSecret = oldSecret;
        }
    }

    public static class Deleted extends SecretWatchEvent implements WatchEvent.Deleted {
        @Inject
        public Deleted(@Assisted final V1Secret secret) {
            super(secret);
        }
    }
}
