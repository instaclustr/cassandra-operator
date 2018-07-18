FROM base-openjre

RUN mkdir -p /opt/{bin,lib/cassandra-sidecar}

COPY sidecar-1.0-SNAPSHOT-final.jar /opt/lib/cassandra-sidecar/cassandra-sidecar.jar
COPY cassandra-sidecar.sh /opt/bin/cassandra-sidecar

ENV PATH = $PATH:/opt/bin

CMD cassandra-sidecar
