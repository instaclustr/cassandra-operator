#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

docker images

helm install \
  cassandra \
  $SCRIPT_DIR/../helm/cassandra \
  --set image.cassandraRepository=gcr.io/cassandra-operator/cassandra-4.0-alpha4 \
  --set image.cassandraTag=latest-dev \
  --set image.sidecarRepository=gcr.io/cassandra-operator/cassandra-sidecar \
  --set image.sidecarTag=latest-dev \
  --set fsGroup=999

while [ "$(kubectl get pods -o wide | grep cassandra-test-cluster-dc1 | grep Running | grep -c "2/2")" = "0" ]
do
  kubectl describe pods
  kubectl logs cassandra-test-cluster-dc1-rack1-0 cassandra
  sleep 5
done