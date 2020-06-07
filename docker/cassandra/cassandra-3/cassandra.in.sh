CASSANDRA_CONF=/etc/cassandra
CASSANDRA_HOME=/usr/share/cassandra
CASSANDRA_CLASSPATH="${CASSANDRA_CONF}:${CASSANDRA_HOME}/*:${CASSANDRA_HOME}/lib/*"
CLASSPATH=${CASSANDRA_CLASSPATH}

JVM_OPTS="-Dcassandra.config=${CASSANDRA_HOME}/cassandra.yaml:/etc/cassandra/cassandra.yaml.d -javaagent:${CASSANDRA_HOME}/agents/jamm.jar"

for options_file in ${CASSANDRA_CONF}/jvm-operator.options
do
	    JVM_OPTS="${JVM_OPTS} $(cat ${options_file} | sed -ne "/^-/p" | tr '\n' ' ')"
done