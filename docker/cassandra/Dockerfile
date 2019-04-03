ARG openjre_image

FROM ${openjre_image}

ARG cassandra_version=3.11.2
ARG cassandra_k8s_addons_jar

COPY install-cassandra /tmp/install-cassandra

RUN /tmp/install-cassandra ${cassandra_version}

COPY entry-point /usr/bin/entry-point

COPY cassandra /usr/sbin/cassandra
COPY nodetool /usr/bin/nodetool
COPY cassandra.in.sh /usr/share/cassandra/
COPY cql-readiness-probe /usr/bin/cql-readiness-probe

COPY ${cassandra_k8s_addons_jar} /usr/share/cassandra/lib

ADD default-config /etc/cassandra

VOLUME /var/lib/cassandra
VOLUME /etc/cassandra

# 7000: intra-node communication
# 7001: TLS intra-node communication
# 7199: JMX
# 9042: CQL
# 9160: thrift service
EXPOSE 7000 7001 7199 9042 9160

ENTRYPOINT ["/usr/bin/entry-point"]
