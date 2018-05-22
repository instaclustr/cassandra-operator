#!/bin/bash

exec -a "cassandra-operator" java \
    -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
    -XX:+PrintFlagsFinal \
    -jar /opt/lib/cassandra-operator/cassandra-operator.jar
    "$@"