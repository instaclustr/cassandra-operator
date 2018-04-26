#!/bin/bash

exec -a "cassandra-operator" java \
    -jar /usr/lib/cassandra-operator/cassandra-operator.jar
    "$@"