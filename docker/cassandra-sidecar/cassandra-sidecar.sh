#!/bin/bash

exec -a "cassandra-sidecar" java \
    -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
    -jar /opt/lib/cassandra-sidecar/cassandra-sidecar.jar \
    "$@"