#!/usr/bin/env bash

# ======== FUNCTION DEFINITIONS ==========

function generate_keystore {
    echo -n "Generate the node keystore ..... "
    keytool -genkeypair -keyalg RSA \
    -alias $(hostname) \
    -keystore certs/${keystore} \
    -storepass ${password} \
    -storetype PKCS12 \
    -keypass ${password} \
    -validity 730 \
    -keysize 2048 \
    -dname "CN=$(hostname), OU=$CLUSTER_NAME, O=Instaclustr, C=CC" \
    -ext "san=dns:${CLUSTER_NAME}-cassandra-seeds,dns:$(hostname -f),ip:$(hostname -i)"
    echo " [Done]"
}

function extract_private_key {
    openssl pkcs12 -in certs/${keystore}  -nodes -nocerts -out nodekey.pem -passin pass:${password}
}

function create_csr {
    echo -n "Generating CSR ....."
    keytool -keystore certs/${keystore} \
    -alias $(hostname) \
    -certreq -file signing_request.csr \
    -ext "san=dns:${CLUSTER_NAME}-cassandra-seeds,dns:$(hostname -f),ip:$(hostname -i)" \
    -keypass ${password} \
    -storepass ${password}

    sed -i'' -e "s/ NEW//g" signing_request.csr

cat <<EOF > a.yaml
apiVersion: certificates.k8s.io/v1beta1
kind: CertificateSigningRequest
metadata:
  name: $(hostname)
spec:
  request: $(cat signing_request.csr | base64 | tr -d '\n')
  usages:
  - digital signature
  - key encipherment
  - server auth
  - client auth
EOF

    kubectl apply -f a.yaml > /dev/null

    echo " [Done]"
    echo
    echo " ==== Waiting for CSR approval ==== "
    echo
    for ((;;))
    do
      kubectl get csr $(hostname) | grep Pending > /dev/null
      if [[ $? != 0 ]]; then
        # Not Pending, break
        break
      fi
      sleep 20
    done
}

function save_certificate {
    echo -n "Fetch the certificate from the signed CSR ....."
    kubectl get csr $(hostname) -o jsonpath='{.status.certificate}' \
        | base64 -d > certs/nodecert.crt
    echo " [Done]"
}

function generate_truststore {
    echo -n "Build the truststore using the root cert ....."
    keytool -keystore certs/${truststore} \
    -storetype PKCS12 \
    -importcert -file ${rootCA} \
    -keypass ${password} \
    -storepass ${password} \
    -alias rootca \
    -noprompt
    echo " [Done]"
}

function populate_keystore {
    echo -n "Populate the keystore ....."
    keytool -keystore certs/${keystore} \
    -alias rootca \
    -storetype PKCS12 \
    -importcert -file ${rootCA} \
    -keypass ${password} \
    -storepass ${password} \
    -noprompt

    keytool -keystore certs/${keystore} \
    -storetype PKCS12 \
    -importcert -file 'certs/nodecert.crt' \
    -keypass ${password} \
    -storepass ${password} \
    -alias $(hostname) \
    -noprompt
    echo " [Done]"
}

function build_cassandra_config {
# ----- Cassandra config ----- #
cat << EOF > config/004-tlsconfig.yaml
server_encryption_options:
    internode_encryption: all
    keystore: ${certs_path}/${keystore}
    keystore_password: ${password}
    require_client_auth: true
    require_endpoint_verification: true
    truststore: ${certs_path}/${truststore}
    truststore_password: ${password}
    protocol: TLS
    cipher_suites: [TLS_RSA_WITH_AES_128_CBC_SHA]
client_encryption_options:
    enabled: true
    optional: false
    keystore: ${certs_path}/${keystore}
    keystore_password: ${password}
    require_client_auth: true
    truststore: ${certs_path}/${truststore}
    truststore_password: ${password}
    protocol: ssl
    cipher_suites: [TLS_RSA_WITH_AES_128_CBC_SHA]
EOF
}

function build_jmx_config {
# ----- Cassandra JMX config ----- #
cat << EOF > env/099-jmx-ssl.sh
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl=true"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl.need.client.auth=false"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.registry.ssl=true"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl.enabled.protocols=TLSv1.2"
JVM_OPTS="\$JVM_OPTS -Dcom.sun.management.jmxremote.ssl.enabled.cipher.suites=TLS_RSA_WITH_AES_256_CBC_SHA"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.keyStore=${certs_path}/${keystore}"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.keyStorePassword=${password}"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.trustStore=${certs_path}/${truststore}"
JVM_OPTS="\$JVM_OPTS -Djavax.net.ssl.trustStorePassword=${password}"
EOF
}

function build_cqlsh_config {
# ----- cqlsh config ----- #

extract_private_key

cat << EOF > cqlshrc
[connection]
hostname = $(hostname)
port = 9042
factory = cqlshlib.ssl.ssl_transport_factory

[ssl]
certfile = ${rootCA}
; Note: If validate = true then the certificate name must match the machine's hostname
validate = true
; If using client authentication (require_client_auth = true in cassandra.yaml) you'll also need to point to your uesrkey and usercert.
; SSL client authentication is only supported via cqlsh on C* 2.1 and greater.
userkey = ${certs_path}/nodekey.pem
usercert = ${certs_path}/nodecert.crt
EOF
}

function build_sidecar_jmx_params {
# ----- jmx params for the sidecar ----- #
    echo -n "--jmx-truststore=${cert_path}/${truststore} --jmx-truststore-password=${password}" > jmx-params
}


# =========== BEGIN HERE ================ #

# Build params
certs_path="/etc/cassandra/certs"
password=$(date +%s | sha256sum | base64 | head -c 32; echo)
keystore="$(hostname)-keystore.p12"
truststore="truststore.p12"
rootCA="/tmp/user-config/certs/rootCA.crt"
account_cert="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"


if [[ ! -e /var/lib/cassandra ]]; then
    echo "Can't find /var/lib/cassandra, bail"
    exit 1
fi

mkdir -p /var/lib/cassandra/tls/{certs,config,env}

# certs dir already there, check if the keystore already exists
if [[ -e "/var/lib/cassandra/tls/certs/${keystore}" ]]; then
    # well, just skip it
    exit 0
fi

cd /var/lib/cassandra/tls
echo "Using hostname $(hostname)"
if [[ ! -e ${rootCA} ]]; then
    rootCA=${account_cert}
fi
echo "Using ${rootCA} as rootCA cert"

# --- Generate certs and populate stores --- #
generate_keystore
create_csr
save_certificate
generate_truststore
populate_keystore

# --- Handle configs --- #
echo -n "Building configs ...."
build_cassandra_config
build_jmx_config
build_cqlsh_config
build_sidecar_jmx_params
echo " [Done]"


echo "All done, continue to launching Cassandra"