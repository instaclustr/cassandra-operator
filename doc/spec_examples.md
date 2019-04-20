## Specific Examples
### TODO - Feel free to submit some examples :)
For the moment check out the master readme.md


### SSL encryption

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

Create a config map with 3 entries:
* a cassandra yaml fragment for configuring node-to-node and client-to-node encryption
* `cqlshrc` to make cqlsh work with ssl
* a shell fragment to install `cqlshrc` into `~/.cassandra/`

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
    # username = cassandra
    # password = cassandra
  install_cqlshrc: |
    mkdir -p ~/.cassandra
    cp \${CASSANDRA_CONF}/cqlshrc ~/.cassandra/cqlshrc
EOF
```

Then deploy a cassandra datacenter using helm and overriding values `userSecretVolumeSource` and `userConfigMapVolumeSource`:
```bash
helm install --wait \
    -n dc1 \
    --set replicaCount=1 \
    --set image.cassandraRepository=gcr.io/cassandra-operator/cassandra \
    --set image.sidecarRepository=gcr.io/cassandra-operator/cassandra-sidecar \
    --set userSecretVolumeSource.secretName=dc1-user-secret \
    --set userConfigMapVolumeSource.name=dc1-user-config \
    --set userConfigMapVolumeSource.items[0].key=cassandra_ssl \
    --set userConfigMapVolumeSource.items[0].path=cassandra.yaml.d/003-ssl.yaml \
    --set userConfigMapVolumeSource.items[1].key=cqlshrc \
    --set userConfigMapVolumeSource.items[1].path=cqlshrc \
    --set userConfigMapVolumeSource.items[2].key=install_cqlshrc \
    --set userConfigMapVolumeSource.items[2].path=cassandra-env.sh.d/003-install-cqlshrc.sh \
    helm/cassandra
```

Verify cassandra started with internode traffic encrypted :
```
kubectl logs cassandra-dc1-cassandra-0 cassandra | grep "Starting Encrypted Messaging Service on SSL port 7001"
```

Test cqlsh connection :
```bash
kubectl exec -it cassandra-dc1-cassandra-0 -- bash -c 'cqlsh $(hostname)'
```
