ARG openjre_image

FROM ${openjre_image}

ARG cassandra_operator_jar

RUN mkdir -p /opt/{bin,lib/cassandra-operator}

COPY ${cassandra_operator_jar} /opt/lib/cassandra-operator/
RUN ln -s /opt/lib/cassandra-operator/${cassandra_operator_jar} /opt/lib/cassandra-operator/cassandra-operator.jar
COPY cassandra-operator.sh /opt/bin/cassandra-operator

ENV PATH = $PATH:/opt/bin

CMD cassandra-operator
