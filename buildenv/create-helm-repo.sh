#!/usr/bin/env bash

cd `dirname "$BASH_SOURCE"`/../helm

helm package cassandra-operator
helm package cassandra
helm repo index .