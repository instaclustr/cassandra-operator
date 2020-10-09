ARG openjre_image

FROM ${openjre_image}

ARG instaclustr_icarus_jar

# Create 'cassandra' user and group
RUN groupadd -g 999 cassandra && useradd -r -u 999 -g cassandra cassandra

RUN mkdir -p /opt/bin && mkdir -p /opt/lib/icarus

COPY ${instaclustr_icarus_jar} /opt/lib/icarus/
RUN ln -s /opt/lib/icarus/${instaclustr_icarus_jar} /opt/lib/icarus/icarus.jar

COPY esop.sh /opt/bin/esop
COPY icarus.sh /opt/bin/icarus
COPY entry-point /usr/bin/entry-point

ENV PATH = $PATH:/opt/bin

# Run as user 'cassandra'
# A numeric UID is used for PSP support:
# https://kubernetes.io/docs/concepts/policy/pod-security-policy/#users-and-groups
USER 999

ENTRYPOINT ["/usr/bin/entry-point"]
