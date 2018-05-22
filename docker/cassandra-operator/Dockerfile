FROM base-openjre

RUN mkdir -p /opt/{bin,lib/cassandra-operator}

COPY operator-1.0-SNAPSHOT-final.jar /opt/lib/cassandra-operator/cassandra-operator.jar
COPY cassandra-operator.sh /opt/bin/cassandra-operator

ENV PATH = $PATH:/opt/bin

CMD cassandra-operator
