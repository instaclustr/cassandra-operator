### SSL encryption

This is an example for running cassandra with ssl encryption using the operator.
Assuming you already have [generated keys](https://docs.datastax.com/en/cassandra/3.0/cassandra/configuration/secureSSLCertWithCA.html)  :
* keystore.jks
* trustore.jks
* cacert.pem (containing the root certificate)

Create a secret with those files :
```bash
kubectl create secret generic dc1-user-secret \
  --from-file=keystore.jks \
  --from-file=truststore.jks \
  --from-file=cacert.pem
```

Create a config map with 2 entries:
* a cassandra yaml fragment for configuring node-to-node and client-to-node encryption
* `cqlshrc` to make cqlsh work with ssl

For instance :
```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: dc1-user-config
data:
  cassandra_ssl: |
    server_encryption_options:
        internode_encryption: all
        keystore: /tmp/user-secret-config/keystore.jks
        # keystore_password: myKeyPass
        truststore: /tmp/user-secret-config/truststore.jks
        #truststore_password: truststorePass
        # More advanced defaults below:
        protocol: TLSv1.2
        algorithm: SunX509
        store_type: JKS
        cipher_suites: [TLS_RSA_WITH_AES_256_CBC_SHA]
        require_client_auth: true
    client_encryption_options:
        enabled: true
        keystore: /tmp/user-secret-config/keystore.jks  ## Path to your .keystore file
        # keystore_password: keystore password  ## Password that you used to generate the keystore
        truststore: /tmp/user-secret-config/truststore.jks  ## Path to your .truststore
        #truststore_password: truststore password  ## Password that you used to generate the truststore
        protocol: TLSv1.2
        store_type: JKS
        algorithm: SunX509
        require_client_auth: false
        cipher_suites: [TLS_RSA_WITH_AES_128_CBC_SHA, TLS_RSA_WITH_AES_256_CBC_SHA]
  cqlshrc: |
    [connection]
    factory = cqlshlib.ssl.ssl_transport_factory
    ssl = true

    [ssl]
    certfile = /tmp/user-secret-config/cacert.pem
    validate = true

    [authentication]
    # username = cassandra // use the one provided during certificate generation
    # password = cassandra // use the one provided during certificate generation
EOF
```

## Deploying the cluster with TLS
The credentials will be added to the cassandra container at the `/tmp/user-secret-config` location, so we use that in the
`cqlshrc` configuration in the configMap example above.
The yaml fragment and the cqlshrc are going to be added to the `/etc/cassandra/` inside the container.

We also use an environment variable **CQLSHRC** to let operator move the `cqlshrc` file
to the default folder where cqlsh will expect to find it (~/.cassandra)

Create a `CassandraDataCenter` CRD that injects the secret, configuration and the environment variable
into the cassandra container:

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: foo-cassandra
  labels:
    app: cassandra
    chart: cassandra-0.1.0
    release: foo
spec:
  replicas: 3
  cassandraImage: "gcr.io/cassandra-operator/cassandra:latest"
  sidecarImage: "gcr.io/cassandra-operator/cassandra-sidecar:latest"
  imagePullPolicy: IfNotPresent
  resources:
    limits:
      memory: 512Mi
    requests:
      memory: 512Mi
  dataVolumeClaim:
    accessModes:
    - ReadWriteOnce
    resources:
      requests:
        storage: 100Mi
  userSecretVolumeSource:
    secretName: dc1-user-secret
  userConfigMapVolumeSource:
    name: dc1-user-config
    #     type is a workaround for https://github.com/kubernetes/kubernetes/issues/68466
    type: array
    items:
      - key: cassandra_ssl
        path: cassandra.yaml.d/003-ssl.yaml
      - key: cqlshrc
        path: cqlshrc
  cassandraEnv:
    - name: CQLSHRC
      value: "/etc/cassandra/cqlshrc"
  prometheusEnabled: false
```

