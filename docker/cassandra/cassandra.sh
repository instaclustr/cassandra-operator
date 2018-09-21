#!/bin/bash -xue

shopt -s nullglob

. /usr/share/cassandra/cassandra.in.sh

JVM_OPTS=${JVM_OPTS:=}

JVM_OPTS="${JVM_OPTS} -Dcassandra.config.loader=com.instaclustr.cassandra.k8s.ConcatenatedYamlConfigurationLoader"
JVM_OPTS="${JVM_OPTS} -Dcassandra.config=/usr/share/cassandra/cassandra.yaml:/etc/cassandra/cassandra.yaml:/etc/cassandra/cassandra.yaml.d"

# provides hints to the JIT compiler
JVM_OPTS="${JVM_OPTS} -XX:CompileCommandFile=${CASSANDRA_HOME}/hotspot_compiler"

# add the jamm agent
JVM_OPTS="${JVM_OPTS} -javaagent:${CASSANDRA_HOME}/agents/jamm-0.3.0.jar"

# sigar support
JVM_OPTS="${JVM_OPTS} -Djava.library.path=${CASSANDRA_HOME}/lib/sigar-bin"

# heap dumps to tmp
JVM_OPTS="${JVM_OPTS} -XX:HeapDumpPath=/var/tmp/cassandra-`date +%s`-pid$$.hprof"


# read additional JVM options from jvm.options files
for options_file in "${CASSANDRA_CONF}/jvm.options" "${CASSANDRA_CONF}/jvm.options.d"/*.options
do
    JVM_OPTS="${JVM_OPTS} "$((sed -ne "/^-/p" | tr '\n' ' ') < "${options_file}")
done

# source additional environment settings
for env_file in "${CASSANDRA_CONF}/cassandra-env.sh" "${CASSANDRA_CONF}/cassandra-env.sh.d"/*.sh
do
    . "${env_file}"
done

exec -a cassandra /usr/bin/java \
    -cp "${CASSANDRA_CLASSPATH}" \
    -ea \
    ${JVM_OPTS} \
    -Dcassandra-foreground=yes \
    org.apache.cassandra.service.CassandraDaemon