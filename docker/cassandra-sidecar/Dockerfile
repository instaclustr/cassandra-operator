ARG openjre_image

FROM ${openjre_image}

ARG cassandra_sidecar_jar

# Create 'cassandra' user and group
RUN groupadd -g 999 cassandra && \
    useradd -r -u 999 -g cassandra cassandra

RUN mkdir -p /opt/{bin,lib/cassandra-sidecar}

COPY ${cassandra_sidecar_jar} /opt/lib/cassandra-sidecar/
RUN ln -s /opt/lib/cassandra-sidecar/${cassandra_sidecar_jar} /opt/lib/cassandra-sidecar/cassandra-sidecar.jar
COPY cassandra-sidecar.sh /opt/bin/cassandra-sidecar

ENV PATH = $PATH:/opt/bin

# Run as user 'cassandra'
# A numeric UID is used for PSP support:
# https://kubernetes.io/docs/concepts/policy/pod-security-policy/#users-and-groups
USER 999

CMD ["cassandra-sidecar"]
