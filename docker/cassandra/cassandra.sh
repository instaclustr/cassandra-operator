#!/bin/bash -ue

echoerr() { echo "$@" 1>&2; }

. /usr/share/cassandra/cassandra.in.sh

JVM_OPTS=${JVM_OPTS:=}

# sanity checks
for conf in "${CASSANDRA_CONF}/cassandra.yaml" "${CASSANDRA_CONF}/cassandra-env.sh"; do
    if [ ! -f "${conf}" ]; then
        echoerr "${conf}: File not found. Required to start Cassandra.";
        exit 1;
    fi

    # Temp support for restore from backups, todo: use cascading yaml loader
    if [ -f "/var/lib/cassandra/tokens.yaml" ]; then
        echo "loading tokens: "
        cat "/var/lib/cassandra/tokens.yaml"
        cat "${CASSANDRA_CONF}/cassandra.yaml" > "/var/lib/cassandra/cassandra-mod.yaml"
        echo $'\n' >> "/var/lib/cassandra/cassandra-mod.yaml"
        cat "/var/lib/cassandra/tokens.yaml" >> "/var/lib/cassandra/cassandra-mod.yaml"
        sed -i '/auto_bootstrap: true/d' "/var/lib/cassandra/cassandra-mod.yaml"
        echo $'\n' >> "/var/lib/cassandra/cassandra-mod.yaml"
        echo "auto_bootstrap: false" >> "/var/lib/cassandra/cassandra-mod.yaml"
        JVM_OPTS="${JVM_OPTS} -Dcassandra.config=file:///var/lib/cassandra/cassandra-mod.yaml"

    else
        JVM_OPTS="${JVM_OPTS} -Dcassandra.config=file:///${CASSANDRA_CONF}/cassandra.yaml"
    fi

done


#JVM_OPTS="${JVM_OPTS} -Dcassandra.config.loader=com.instaclustr.cassandra.k8s.CascadingYamlConfigurationLoader"
JVM_OPTS="${JVM_OPTS} -Dcassandra.storagedir=/var/lib/cassandra" # set via YAML

# provides hints to the JIT compiler
JVM_OPTS="${JVM_OPTS} -XX:CompileCommandFile=/usr/share/cassandra/hotspot_compiler"

# add the jamm javaagent
JVM_OPTS="${JVM_OPTS} -javaagent:${CASSANDRA_HOME}/agents/jamm-0.3.0.jar"

# sigar support
JVM_OPTS="${JVM_OPTS} -Djava.library.path=${CASSANDRA_HOME}/lib/sigar-bin"

# heap dumps to tmp
JVM_OPTS="${JVM_OPTS} -XX:HeapDumpPath=/var/tmp/cassandra-`date +%s`-pid$$.hprof"


# Read additional JVM options from jvm.options file
JVM_OPTS="${JVM_OPTS} "$((sed -ne "/^-/p" | tr '\n' ' ') < ${CASSANDRA_CONF}/jvm.options)


. ${CASSANDRA_CONF}/cassandra-env.sh


exec -a cassandra /usr/bin/java \
    -cp "${CASSANDRA_CLASSPATH}" \
    ${JVM_OPTS} \
    -Dcassandra-foreground=yes \
    org.apache.cassandra.service.CassandraDaemon