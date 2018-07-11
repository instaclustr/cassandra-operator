FROM base-openjre

ARG cassandra_version=3.11.2

COPY install-cassandra /tmp/install-cassandra

RUN /tmp/install-cassandra ${cassandra_version}

COPY cassandra.sh /usr/sbin/cassandra
COPY nodetool.sh /usr/bin/nodetool
COPY cassandra.in.sh /usr/share/cassandra/
COPY readiness-probe.sh /usr/bin/readiness-probe

COPY k8s-addons-1.0-SNAPSHOT.jar /usr/share/cassandra/lib

VOLUME /var/lib/cassandra
VOLUME /etc/cassandra

# 7000: intra-node communication
# 7001: TLS intra-node communication
# 7199: JMX
# 9042: CQL
# 9160: thrift service
# 9200: elassandra HTTP
# 9300: elasticsearch transport
EXPOSE 7000 7001 7199 9042 9160 9200 9300

CMD ["/usr/sbin/cassandra"]