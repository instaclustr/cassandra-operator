ARG openjre_image

FROM ${openjre_image}

ARG cassandra_sidecar_jar

RUN mkdir -p /opt/{bin,lib/cassandra-sidecar}

COPY ${cassandra_sidecar_jar} /opt/lib/cassandra-sidecar/
RUN ln -s /opt/lib/cassandra-sidecar/${cassandra_sidecar_jar} /opt/lib/cassandra-sidecar/cassandra-sidecar.jar
COPY cassandra-sidecar.sh /opt/bin/cassandra-sidecar

ENV PATH = $PATH:/opt/bin

CMD cassandra-sidecar

