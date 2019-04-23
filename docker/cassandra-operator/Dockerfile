FROM debian:stretch

RUN mkdir -p /opt/{bin,lib/cassandra-operator}

COPY cassandra-operator.sh /opt/bin/cassandra-operator

ENV PATH = $PATH:/opt/bin

CMD cassandra-operator
