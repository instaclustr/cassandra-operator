#!/bin/bash

exec -a "cassandra-sidecar" java \
    -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm  \
    -jar /opt/lib/cassandra-sidecar/cassandra-sidecar.jar cassandra-sidecar \
    "$@"