ARG openjre_image

FROM ${openjre_image}

ARG cassandra_major_version=3
ARG cassandra_version=3.11.9
ARG cassandra_exporter_version=0.9.10
ARG apache_mirror_url=https://dl.bintray.com/apache/cassandra/pool/main/c/cassandra
ARG install_cassandra_exporter=true

COPY cassandra-${cassandra_major_version}/install-cassandra.sh /tmp/install-cassandra.sh
COPY cassandra-${cassandra_major_version}/debs /tmp

RUN C_APACHE_MIRROR_URL=${apache_mirror_url} \
    INSTALL_CASSANDRA_EXPORTER=${install_cassandra_exporter} \
    /tmp/install-cassandra.sh ${cassandra_version} ${cassandra_exporter_version}

COPY entry-point /usr/bin/entry-point
COPY wrapper /usr/bin/wrapper

# Allow entrypoint script to modify ulimit by creating a "patched" Bash executable
# which is allowed to use the required capabilities
RUN cp /bin/bash /bin/bash-mod \
    && setcap cap_ipc_lock=+ep /bin/bash-mod \
    && setcap cap_sys_resource=+ep /bin/bash-mod

COPY cassandra-${cassandra_major_version}/cassandra /usr/sbin/cassandra
COPY cassandra-${cassandra_major_version}/nodetool /usr/bin/nodetool
COPY cassandra-${cassandra_major_version}/cassandra.in.sh /usr/share/cassandra/
COPY cassandra-${cassandra_major_version}/cql-readiness-probe /usr/bin/cql-readiness-probe
COPY cassandra-${cassandra_major_version}/jars /usr/share/cassandra/lib

ADD cassandra-${cassandra_major_version}/default-config /etc/cassandra
RUN chown -R cassandra:cassandra /etc/cassandra

VOLUME /var/lib/cassandra
VOLUME /etc/cassandra

# 7000: intra-node communication
# 7001: TLS intra-node communication
# 7199: JMX
# 9042: CQL
# 9160: thrift service
EXPOSE 7000 7001 7199 9042 9160

# Run as user 'cassandra'
# A numeric UID is used for PSP support:
# https://kubernetes.io/docs/concepts/policy/pod-security-policy/#users-and-groups
# The user is created in ./install-cassandra.sh
USER 999

ENTRYPOINT ["/usr/bin/wrapper"]
