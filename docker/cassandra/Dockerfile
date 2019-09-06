ARG openjre_image

FROM ${openjre_image}

ARG cassandra_version=3.11.4
ARG cassandra_k8s_addons_jar

COPY install-cassandra /tmp/install-cassandra

RUN /tmp/install-cassandra ${cassandra_version}

COPY entry-point /usr/bin/entry-point
COPY wrapper /usr/bin/wrapper

# Allow entrypoint script to modify ulimit by creating a "patched" Bash executable
# which is allowed to use the required capabilities
RUN cp /bin/bash /bin/bash-mod \
    && setcap cap_ipc_lock=+ep /bin/bash-mod \
    && setcap cap_sys_resource=+ep /bin/bash-mod

COPY cassandra /usr/sbin/cassandra
COPY nodetool /usr/bin/nodetool
COPY cassandra.in.sh /usr/share/cassandra/
COPY cql-readiness-probe /usr/bin/cql-readiness-probe

COPY ${cassandra_k8s_addons_jar} /usr/share/cassandra/lib

ADD default-config /etc/cassandra
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
# The user is created in ./install-cassandra
USER 999

ENTRYPOINT ["/usr/bin/wrapper"]
