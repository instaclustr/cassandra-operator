# Auth

Cassandra operator is by default set up with the following 
configuration properties in CDC spec (1):

```
cassandraAuth:
  authenticator: AllowAllAuthenticator
  authorizer: AllowAllAuthorizer
  roleManager: CassandraRoleManager
```

By default, Cassandra will not ask you for any password and it will behave as if the authentication 
is completely turned off.

You can change it to classic password-based auth like the following (2): 

```
  cassandraAuth:
    authenticator: PasswordAuthenticator
    authorizer: CassandraAuthorizer
    roleManager: CassandraRoleManager
```

When you are starting your cluster from scratch, e.g. from 1 to 3 nodes, Cassandra 
sets up `system_auth` keyspace which has the replication strategy of `SimpleStrategy` and replication factor of 1.

This might work all fine but since nodes are added to a cluster and you have RF 1, data 
are stored just on one node. If you scale that cluster down and a node which happened to go down is the 
node with your auth data, you might not be able to log in.

Therefore it is necessary to change you replication factor to number of nodes in a cluster and replication 
strategy should be changed to `NetworkTopologyStrategy` (especially of your cluster has some 
rack-awareness). After that, on each node, you should do:

```
cqlsh>  ALTER KEYSPACE system_auth WITH replication = {'class': 'NetworkTopologyStrategy', 'test-dc-cassandra': 3};
```

In the above example, if your cluster happens to have 3 nodes, alter your `system_auth` keyspace after 
all nodes are up like this by logging in to any node and executing the above. `test-dc-cassandra` is name of your datacenter.

The above command only specified that from now on, your `system_auth` keyspace has different network topology 
strategy and each piece of data will be stored three times (on three different nodes). It does not mean that 
by altering of your keyspace, these data are really there. There is not magic behind that. You need to do it 
manually like this: 

```
$ nodetool -h ip.of.a.pod repair -full
```

where `ip.of.a.pod` is an IP / hostname of a respective pod in a cluster. Now if you scale your cluster down from 
e.g. 4 nodes to just 2, you will be still able to log in without any problems.

**You have to execute this nodetool command on every node.** 

You should repeat this technique as you see it fit. If you have e.g. 5 nodes, your system_auth 
should be replicated everywhere.

### Switching between AllowAllAuthenticator to PasswordAuthenticator

If you happened to deploy your cluster without password authentication turned on, you might want to do that on live 
cluster. Before proceeding, be sure you have changed your replication strategy to `NetworkTopologyStrategy` and you 
repaired all nodes.

Only after you do that, change `spec.cassandraAuth` in your YAML to contain same properties as it is specified in (2) 
(all of them). Apply that spec as you are used to (`kubectl apply -f your_spec.yaml`). It is applied now but pods are 
still running with old Cassandra configuration so you have to restart nodes one by one. Restarting a pod a Cassandra 
node is running in is quite a tricky task but we will just _delete_ that pod. Once it is deleted, operator sees that 
the stateful set has _n-1_ pods running but it should have _n_ pods running so it will start the same pod we just took down.
Deletion of a pod is done like:

```
$ kubectl delete pod cassandra-test-dc-cassandra-west1-a-0
```

Since we are using persistent volumes, these volumes are still there even we delete our pod so 
if a pod is restarted, it will reuse same persistent volumes were our data are. You can check that 
operator is restarting you pod after you delete that, automatically.

Persistent volumes are deleted automatically only in case you have set flag `deletePVCs` in your spec to true.
You might want to set it back to `false` and delete finalizers in spec so your volumes are not deleted on pod deletion. 

## `cassandra` role

The `cassandra` role is good old default role you know from 
out-of-the-box Cassandra installation. The default password 
is `cassandra` and it has to be specified on your logging attempts. 
Default password should be changed etc.

## `probe` role

This role is a _technical_ role. We are checking if a Cassandra node is truly up from Kubernetes 
perspective by invoking `cqlsh` select command on some system keyspace. If this query returns a meaningful 
result, we are sure that particular node is indeed up and responsive.

If we used `cassandra` role for this task, the password might change over time by a user of that cluster 
as such so we would not know what password to use upon such queries.

The _Readiness probe_ is a Kubernetes feature and it is executed periodically every _n_ seconds. 
`probe` role is created on the very first readiness probe execution and it is hardcoded to be _non-superuser_ role.

# SSL

In order to enable SSL on Cassandra cluster, you have to create a secret where your all files will be stored:

```
kubectl create secret generic test-cassandra-dc-ssl \
    --from-file=keystore.p12 \
    --from-file=truststore.jks \
    --from-file=ca-cert \
    --from-file=client.cer.pem \
    --from-file=client.key.pem
```

After that, you have to create a `ConfigMap` which will specify the Cassandra configuration fragments as well as 
cqlshrc file for the connection to a Cassandra node from inside a Cassandra container via `cqlsh`:

```
apiVersion: v1
kind: ConfigMap
metadata:
  # name of our config map, we will use it for further reference
  name: test-dc-cassandra-user-config
data:
  # config map entry called “cassandra_ssl” with two sections
  # the content of this entry is the very Cassandra configuration to be applied
  cassandra_ssl: |
    # server encryption section says how nodes will be secured between themselves
    server_encryption_options:
        # we require the communication to be encrypted every time
        # the other option is to encrypt e.g. only the traffic between 
        # different Cassandra data centers 
        internode_encryption: all 
        # path to our keystore, note here we are referencing to 
        # /tmp/user-secret, which is a Kubernetes volume mounted
        keystore: /tmp/user-secret/keystore.p12
        # password to keystore
        keystore_password: cassandra
        # similar entry for truststore as it is done for keystore
        truststore: /tmp/user-secret/truststore.jks
        # password for truststore
        truststore_password: cassandra
        # other configuration
        protocol: TLS 
        algorithm: SunX509
        store_type: JKS 
        cipher_suites: [TLS_RSA_WITH_AES_256_CBC_SHA]
        require_client_auth: true
    # client encryption section says how a node is expecting to be told from a client
    client_encryption_options:
        enabled: true
        # path to keystore, same as for server options
        keystore: /tmp/user-secret/keystore.p12
        keystore_password: cassandra
        # path to truststore, same as for server options
        truststore: /tmp/user-secret/truststore.jks
        truststore_password: cassandra
        # other options
        protocol: TLS 
        store_type: JKS 
        algorithm: SunX509
        require_client_auth: true
        cipher_suites: [TLS_RSA_WITH_AES_256_CBC_SHA]
  # ConfigMap entry where the content of cqlshrc file is
  cqlshrc: |
    [connection]
    port = 9042
    factory = cqlshlib.ssl.ssl_transport_factory
    ssl = true
    [ssl]
    # location of ca-cert file from user’s secret
    certfile = /tmp/user-secret/ca-cert
    validate = true
    # location of client.cert.pem and client.key.pem we will mount 
    # under /tmp/user-secret from secret source volume
    usercert = /tmp/user-secret/client.cer.pem
    userkey = /tmp/user-secret/client.key.pem
  # script copying cqlshrc above under ~/.cassandra/cqlshrc of cassandra user
  install_cqlshrc: |
    mkdir -p ~/.cassandra
    cp /etc/cassandra/cqlshrc ~/.cassandra/cqlshrc
```

After this is set, you have to add this configuration in your CassandraDataCenter CDC spec:

```
spec:
  userSecretVolumeSource:
    # name of custom secret we created with all our files
    secretName: test-cassandra-dc-ssl
  userConfigMapVolumeSource:
    # reference to our config map above
    name: test-dc-cassandra-user-config
    type: array
    items:
      # all paths are relative to /etc/cassandra
      - key: cassandra_ssl 
        path: cassandra.yaml.d/003-ssl.yaml
      - key: cqlshrc
        path: cqlshrc
      - key: install_cqlshrc
        path: cassandra-env.sh.d/003-install-cqlshrc.sh
```

If you start a cluster as you are used to, check out the logs by 

$ kubectl logs -f _name_of_the_pod_ cassandra

You have to see these logging output to see TLS was applied:

```
INFO  [main] MessagingService.java:704 Starting Encrypted Messaging Service on SSL port 7001
... 
INFO  [main] Server.java:159 Starting listening for CQL clients on 
cassandra-test-dc-cassandra-west1-a-0.cassandra.default.svc.cluster.local/10.244.2.23:9042 (encrypted)...
```

Once you are in a Cassandra container, you should see this in 
 `/home/cassandra/.cassandra/cqlshrc`:

```
$ cat /home/cassandra/.cassandra/cqlshrc 
[connection]
port = 9042
factory = cqlshlib.ssl.ssl_transport_factory
ssl = true
[ssl]
certfile = /tmp/user-secret/ca-cert
validate = true
usercert = /tmp/user-secret/client.cer.pem
userkey = /tmp/user-secret/client.key.pem
```

You should be able to connect. This connection will be secured because `ssl` was set to `true` in `cqlshrc`.

```
$ cqlsh -u cassandra -p cassandra $(hostname)
Connected to test-dc-cassandra at cassandra-test-dc-cassandra-west1-a-0:9042.
[cqlsh 5.0.1 | Cassandra 3.11.5 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cassandra@cqlsh> 
``` 