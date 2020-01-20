package org.apache.cassandra.auth;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.apache.cassandra.config.DatabaseDescriptor;

public class KubernetesRoleManager extends CassandraRoleManager {
    @Override
    public Set<Option> supportedOptions() {
        return DatabaseDescriptor.getAuthenticator().getClass() == KubernetesAuthenticator.class
                ? ImmutableSet.of(Option.LOGIN, Option.SUPERUSER, Option.PASSWORD)
                : ImmutableSet.of(Option.LOGIN, Option.SUPERUSER);
    }

    @Override
    public boolean canLogin(final RoleResource role) {
        if (role.getName().equals("cassandra")) {
            return true;
        }

        return super.canLogin(role);
    }
}