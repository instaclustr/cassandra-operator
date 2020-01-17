package org.apache.cassandra.auth;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isReadable;
import static org.apache.cassandra.auth.AuthKeyspace.ROLES;
import static org.apache.cassandra.auth.CassandraRoleManager.consistencyForRole;
import static org.apache.cassandra.config.SchemaConstants.AUTH_KEYSPACE_NAME;
import static org.apache.cassandra.cql3.QueryOptions.forInternalCalls;
import static org.apache.cassandra.cql3.QueryProcessor.getStatement;
import static org.apache.cassandra.service.QueryState.forInternalCalls;
import static org.apache.cassandra.utils.ByteBufferUtil.bytes;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.apache.cassandra.config.Schema;
import org.apache.cassandra.cql3.RoleName;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.cql3.statements.CreateRoleStatement;
import org.apache.cassandra.cql3.statements.RevokePermissionsStatement;
import org.apache.cassandra.cql3.statements.SelectStatement;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesAuthenticator extends PasswordAuthenticator {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesAuthenticator.class);

    private static final String CQL_PROBE_USER_NAME = "probe";
    private static final Path CQL_PROBE_SECRET = Paths.get("/etc/cassandra/.cql-probe-secret");

    private static final String SIDECAR_USER_NAME = "sidecar";
    private static final Path SIDECAR_SECRET = Paths.get("/etc/cassandra/.sidecar-secret");

    private static final String ADMIN_USER_NAME = "admin";

    private static final String DEFAULT_CASSANDRA_USER_NAME = "cassandra";
    private static final Path ALLOW_DEFAULT_CASSANDRA_USER = Paths.get("/etc/cassandra/.allow-default-cassandra-user");

    public static final String AUTHENTICATION_FAILED_MESSAGE_MISSING_FILE = "Authentication failed. Secret file " + ALLOW_DEFAULT_CASSANDRA_USER +
            " does not exist or is not readable. You can login under 'cassandra' only if file " + ALLOW_DEFAULT_CASSANDRA_USER + " exists on Cassandra node.";
    public static final String AUTHENTICATION_FAILED_MESSAGE_PASSWORD_DONT_MATCH = "Authentication failed. Password does not match.";

    private static final String SALTED_HASH = "salted_hash";
    private SelectStatement authenticateStatement;
    private SelectStatement legacyAuthenticateStatement;

    @Override
    public void setup() {
        authenticateStatement = prepare(format("SELECT %s FROM %s.%s WHERE role = ?", SALTED_HASH, AUTH_KEYSPACE_NAME, ROLES));

        if (Schema.instance.getCFMetaData(AUTH_KEYSPACE_NAME, LEGACY_CREDENTIALS_TABLE) != null)
            prepareLegacyAuthenticateStatement();
    }

    @Override
    public SaslNegotiator newSaslNegotiator(InetAddress clientAddress) {
        return new PlainTextSaslAuthenticator();
    }

    private AuthenticatedUser authenticate(String username, String password) throws AuthenticationException {

        if (username.equals(DEFAULT_CASSANDRA_USER_NAME)
                && (!exists(ALLOW_DEFAULT_CASSANDRA_USER) || !isReadable(ALLOW_DEFAULT_CASSANDRA_USER))) {
            throw new AuthenticationException(AUTHENTICATION_FAILED_MESSAGE_MISSING_FILE);
        }

        if (username.equals(ADMIN_USER_NAME)) {
            return createServiceRoleIfNotExists(getAdminRoleName(), true, ADMIN_USER_NAME);
        }

        if (username.equals(CQL_PROBE_USER_NAME)) {
            compareServiceUserPassword(CQL_PROBE_SECRET, username, password);
            return createServiceRoleIfNotExists(getProbeRoleName(), false, null);
        } else if (username.equals(SIDECAR_USER_NAME)) {
            compareServiceUserPassword(SIDECAR_SECRET, username, password);
            return createServiceRoleIfNotExists(getSidecarRoleName(), false, null);
        }

        if (!checkpw(password, getHashedPasswordFromDB(username))) {
            throw new AuthenticationException(format("Provided username %s and/or password are incorrect", username));
        }

        return new AuthenticatedUser(username);
    }

    private String getHashedPasswordFromDB(String username) {

        ResultMessage.Rows rows = authenticationStatement().execute(forInternalCalls(),
                                                                    forInternalCalls(consistencyForRole(username),
                                                                                     newArrayList(bytes(username))),
                                                                    nanoTime());
        if (rows.result.isEmpty()) {
            throw new AuthenticationException(format("Role %s does not exist.", username));
        }

        final UntypedResultSet result = UntypedResultSet.create(rows.result);

        if (!result.one().has(SALTED_HASH)) {
            throw new AuthenticationException(format("Role %s exists but it does not have password.", username));
        }

        return result.one().getString(SALTED_HASH);
    }

    private void compareServiceUserPassword(Path secretFile, String username, String password) {
        String sharedSecret = null;
        try {
            sharedSecret = Files.readAllLines(secretFile, Charset.defaultCharset()).get(0);
        } catch (final Exception e) {
            logger.error(format("Failed to load secret from %s for user %s.", secretFile, username));
        }

        if (sharedSecret != null && !password.equals(sharedSecret))
            throw new AuthenticationException(AUTHENTICATION_FAILED_MESSAGE_PASSWORD_DONT_MATCH);
    }

    @Override
    public AuthenticatedUser legacyAuthenticate(final Map<String, String> credentials) throws AuthenticationException {
        return authenticate(credentials.get("username"), credentials.get("password"));
    }

    /**
     * If the legacy users table exists try to verify credentials there. This is to handle the case
     * where the cluster is being upgraded and so is running with mixed versions of the authn tables
     */
    private SelectStatement authenticationStatement() {
        if (Schema.instance.getCFMetaData(AUTH_KEYSPACE_NAME, LEGACY_CREDENTIALS_TABLE) == null)
            return authenticateStatement;
        else {
            // the statement got prepared, we to try preparing it again.
            // If the credentials was initialised only after statement got prepared, re-prepare (CASSANDRA-12813).
            if (legacyAuthenticateStatement == null)
                prepareLegacyAuthenticateStatement();
            return legacyAuthenticateStatement;
        }
    }

    private void prepareLegacyAuthenticateStatement() {
        String query = format("SELECT %s from %s.%s WHERE username = ?", SALTED_HASH, AUTH_KEYSPACE_NAME, LEGACY_CREDENTIALS_TABLE);
        legacyAuthenticateStatement = prepare(query);
    }

    private static SelectStatement prepare(String query) {
        return (SelectStatement) getStatement(query, ClientState.forInternalCalls()).statement;
    }

    private AuthenticatedUser createServiceRoleIfNotExists(RoleName role, boolean superuser, String password) {
        final RoleOptions roleOptions = new RoleOptions();
        roleOptions.setOption(IRoleManager.Option.LOGIN, true);
        roleOptions.setOption(IRoleManager.Option.SUPERUSER, superuser);

        if (password != null) {
            roleOptions.setOption(IRoleManager.Option.PASSWORD, password);
        }

        final ClientState clientState = ClientState.forInternalCalls();
        clientState.login(new AuthenticatedUser(DEFAULT_CASSANDRA_USER_NAME));

        new CreateRoleStatement(role, roleOptions, true).execute(clientState);

        revokePermissions(clientState, role);

        return new AuthenticatedUser(role.getName());
    }

    private RoleName getRoleName(String roleName) {
        final RoleName role = new RoleName();
        role.setName(roleName, true);
        return role;
    }

    private RoleName getSidecarRoleName() {
        return getRoleName(SIDECAR_USER_NAME);
    }

    private RoleName getProbeRoleName() {
        return getRoleName(CQL_PROBE_USER_NAME);
    }

    private RoleName getAdminRoleName() {
        return getRoleName(ADMIN_USER_NAME);
    }

    private void revokePermissions(ClientState clientState, RoleName roleName) {
        if (roleName.getName().equals(CQL_PROBE_USER_NAME)) {
            revokePermissionsForProbeRole(clientState, roleName);
        } else if (roleName.getName().equals(SIDECAR_USER_NAME)) {
            revokePermissionsForSidecarRole(clientState, roleName);
        }
    }

    private void revokePermissionsForSidecarRole(ClientState clientState, RoleName sidecarRoleName) {
        // todo
    }

    private void revokePermissionsForProbeRole(ClientState clientState, RoleName probeRole) {
        new RevokePermissionsStatement(DataResource.root().applicablePermissions(), DataResource.root(), probeRole).execute(clientState);
        new RevokePermissionsStatement(FunctionResource.root().applicablePermissions(), FunctionResource.root(), probeRole).execute(clientState);
        new RevokePermissionsStatement(JMXResource.root().applicablePermissions(), JMXResource.root(), probeRole).execute(clientState);
        new RevokePermissionsStatement(RoleResource.root().applicablePermissions(), RoleResource.root(), probeRole).execute(clientState);
    }

    private class PlainTextSaslAuthenticator implements SaslNegotiator {
        private boolean complete = false;
        private String username;
        private String password;

        public byte[] evaluateResponse(byte[] clientResponse) throws AuthenticationException {
            decodeCredentials(clientResponse);
            complete = true;
            return null;
        }

        public boolean isComplete() {
            return complete;
        }

        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException {
            if (!complete)
                throw new AuthenticationException("SASL negotiation not complete");
            return authenticate(username, password);
        }

        /**
         * SASL PLAIN mechanism specifies that credentials are encoded in a
         * sequence of UTF-8 bytes, delimited by 0 (US-ASCII NUL).
         * The form is : {code}authzId<NUL>authnId<NUL>password<NUL>{code}
         * authzId is optional, and in fact we don't care about it here as we'll
         * set the authzId to match the authnId (that is, there is no concept of
         * a user being authorized to act on behalf of another with this IAuthenticator).
         *
         * @param bytes encoded credentials string sent by the client
         * @throws org.apache.cassandra.exceptions.AuthenticationException if either the
         *                                                                 authnId or password is null
         */
        private void decodeCredentials(byte[] bytes) throws AuthenticationException {
            logger.trace("Decoding credentials from client token");
            byte[] user = null;
            byte[] pass = null;
            int end = bytes.length;
            for (int i = bytes.length - 1; i >= 0; i--) {
                if (bytes[i] == NUL) {
                    if (pass == null)
                        pass = Arrays.copyOfRange(bytes, i + 1, end);
                    else if (user == null)
                        user = Arrays.copyOfRange(bytes, i + 1, end);
                    else
                        throw new AuthenticationException("Credential format error: username or password is empty or contains NUL(\\0) character");

                    end = i;
                }
            }

            if (pass == null || pass.length == 0)
                throw new AuthenticationException("Password must not be null");
            if (user == null || user.length == 0)
                throw new AuthenticationException("Authentication ID must not be null");

            username = new String(user, StandardCharsets.UTF_8);
            password = new String(pass, StandardCharsets.UTF_8);
        }
    }
}
