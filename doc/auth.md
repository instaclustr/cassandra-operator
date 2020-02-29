# Auth

Cassandra operator is by default set up with the following 
configuration properties in CDC spec:

```
cassandraAuth:
  authenticator: AllowAllAuthenticator
  authorizer: AllowAllAuthorizer
  roleManager: CassandraRoleManager
```

By default, Cassandra will not ask you for any password and it will behave as if the authentication 
is completely turned off.

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

You can change it to classic password-based auth like the following: 

```
  cassandraAuth:
    authenticator: PasswordAuthenticator
    authorizer: CassandraAuthorizer
    roleManager: CassandraRoleManager
```

If you `apply` a spec again, since pods and nodes in them are still running, this change is not propagated automatically. 
Hence, you have to _restart_ that container. We are restartring a container by invoking _restart operation_ again its Sidecar 
container. There is HTTP server in Sidecar container which listens to various operations, `restart` being one of them.

If an IP of a pod is 10.20.30.40, you have to `POST` the following body as `application/json` to `http://10.20.30.40:4567/operations`

```
{
	"type": "restart"
}
```

Under the hood, restarting of a container means that Cassandra node is _drained_ first and then it leaves a ring. 
Afterwards, its process (pid 1) is killed from Sidecar calling Kubernetes' `exec` command from Sidecar against Cassandra 
container. Once that process is killed, container exists, so it is automatically restarted by Kubernetes, but now 
it picks up different configuration (password-based) which we applied before. 

Since we are using persistent volumes, these volumes are still there even we delete our pod so 
if a container is restarted, it will reuse same persistent volumes were our data are. You can check that 
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
[cqlsh 5.0.1 | Cassandra 3.11.6 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cassandra@cqlsh> 
``` 

## How is CQL probe working?

From Kubernetes point of view, Kubernetes has to have a way how to check if a container is up or not. This is done by _readiness probe_. In our case, it is a simple script in Cassandra container in [/usr/bin/cql-rediness-probe](https://github.com/instaclustr/cassandra-operator/blob/master/docker/cassandra/cql-readiness-probe). The logic is simple, if we are on the password authenticator, we have to log in with a password. We are using `probe` role for this. If that role 
does not exist yet, it is created as non-super-user. Check the fact that we are using `cassandra:cassanra` to create it because the probe role will be the very first CQL statement against started Cassandra and it does not matter you change this password for `cassandra` afterwards because probe role would be already created. We are also not using any SSL-like configuration for `cqlsh` command itself because this is all done transparently in `/home/cassandra/cassandra/.cqlshrc`. Be sure you have this file populated with the configuration above otherwise that probe wil fail to connect.

## SSL with Sidecar

Once you are on SSL, the interesting (or rather, quite obvious) fact is that whatever CQL request you would do against Cassandra, it will fail because it started to be secured (if you enabled client-node SSL, which is most probably the case). If you configed your Cassandra node to be on SSL, well, Sidecar has to know how to talk to Cassandra securely too. Please keep in mind that right now, the only case this configuration needs to be in place is the situation you are restring a node by calling `restart` operation above as that one internally tries to check if a node is back online by executing some CQL statement against it.

In order to achieve this, we use the following config map:

```
$ kubectl describe configmap cassandra-operator-sidecar-config-test-dc
Name:         cassandra-operator-sidecar-config-test-dc
Namespace:    default
Labels:       <none>
Annotations:  <none>

Data
====
cassandra-config:
----
datastax-java-driver {
  advanced.ssl-engine-factory {
    class = DefaultSslEngineFactory

    cipher-suites = [ "TLS_RSA_WITH_AES_256_CBC_SHA" ]
    
    hostname-validation = false

    truststore-path = /tmp/sidecar-secret/truststore.jks
    truststore-password = cassandra
    keystore-path = /tmp/sidecar-secret/keystore.p12
    keystore-password = cassandra
  }
}

Events:  <none>
```

Few things to check:

* the name of this config map follows a pattern. It is _cassandra-operator-sidecar-config-name-of-dc_.
* the name of config map entry is in every case _cassandra-config_
* the content of _cassandra-config_ is a configuration fragment of Cassandra Datastax Driver of version 4.
* truststore and keystore paths are referencing the very same files you used in `cassandra.yaml` configration

As a reference to further dig deeper, we are using custom `DriverConfigLoader` from Datastax 4 driver which is not fetching configration from a file but it is fetching it by calling Kubernetes API (by official Kubernetes Java client) and it reads that configuration from there. The configuration retrieval mechanism is pluggable in Datastax driver of version 4 so we are using this configuration mechanism transparently here. One just have to be sure to create a config map of right name and follow the naming pattern and this configuration will be resolved automatically. If you feel brave enough to follow the code in more depth, you are welcome do to so in the below links, .e.g in helper, you see how names of configs and other things are resolved.

* [KubernetesCassandraConfigReader](https://github.com/instaclustr/instaclustr-commons/blob/master/src/main/java/com/instaclustr/cassandra/service/kubernetes/KubernetesCassandraConfigReader.java)

* [KubernetesCqlSession](https://github.com/instaclustr/instaclustr-commons/blob/master/src/main/java/com/instaclustr/cassandra/service/kubernetes/KubernetesCqlSession.java#L53-L87)

* [CassandraKubernetesHelper](https://github.com/instaclustr/instaclustr-commons/blob/master/src/main/java/com/instaclustr/cassandra/service/kubernetes/CassandraKubernetesHelper.java)

You might argue that the configuration here is security sensitive as it contains passwords. Fair enough. If you do not want to have them there, you might put them into a secret. The very same configuration mechanism holds. For example, imagine that you want your Sidecar to talk to a Cassandra node with different username and password from `cassandra:cassandra` which is used by default and you are using `PasswordAuthenticator`. In that case, you need to configure Sidecar accordingly so it is "password-aware" and it does not use defaults. In that case, you would create this secret:

```
datastax-java-driver {
  advanced.auth-provider {
    class = PlainTextAuthProvider
    username = cassandra
    password = cassandra
  }
}
```

This secret would have name _cassandra-operator-sidecar-secret-name-of-dc_ and its only entry would have name `cassandra-config`.

This custom configuration mechanism has various advantages. Imagine your password has changed or you have changed some configuration parameters so the driver has to cope with that. Normally, you would the most probably restart the pod / container or something similar so such container would "re-fetch" its configration. But since we are constructing `CqlSession` per request, we actually retrieve configuration and secrets for every `CqlSession` created so if you update your config maps or secrets from outside, it will be transparently propagated into Sidecar container as if nothing happened. Eventually, you should not see your probe failing anymore.
