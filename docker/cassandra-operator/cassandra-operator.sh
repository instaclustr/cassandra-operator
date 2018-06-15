#!/bin/bash

exec -a "cassandra-operator" java \
    -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
    -jar /opt/lib/cassandra-operator/cassandra-operator.jar \
    "$@"