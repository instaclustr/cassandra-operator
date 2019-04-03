#!/bin/bash -ue

. /usr/share/cassandra/cassandra.in.sh

exec -a nodetool /usr/bin/java -cp "${CASSANDRA_CLASSPATH}" org.apache.cassandra.tools.NodeTool $@