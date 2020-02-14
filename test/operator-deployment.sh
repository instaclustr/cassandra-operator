#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

docker images

helm \
  install \
  cassandra-operator \
  $SCRIPT_DIR/../helm/cassandra-operator \
  --set image.repository=gcr.io/cassandra-operator/cassandra-operator \
  --set image.tag=latest-dev

while [ "$(kubectl get pods -o wide | grep operator | grep -c Running)" = "0" ]
do
  kubectl describe pods
  sleep 10
done