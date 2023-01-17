#!/bin/bash -xue

cassandra_version=$1
cassandra_exporter_version=$2

# create 'cassandra' user and group
groupadd -g 999 cassandra
useradd -m -r -u 999 -g cassandra cassandra

pkg_dir=$(mktemp -d) && chmod 755 "${pkg_dir}"
arch="all"
arch_pkg_dir="${pkg_dir}/${arch}" && mkdir "${arch_pkg_dir}"

C_APACHE_MIRROR_URL="${C_APACHE_MIRROR_URL:-https://apache.jfrog.io/artifactory/cassandra-deb/pool/main/c/cassandra}"
INSTALL_CASSANDRA_EXPORTER="${INSTALL_CASSANDRA_EXPORTER:-true}"

if [ "$(find /tmp -type f -name '*.deb' | wc -l)" != "0" ]; then
  mv /tmp/cassandra*.deb ${arch_pkg_dir}
else
  # download the C* packages
  (cd "${arch_pkg_dir}" &&
      curl -SLO "${C_APACHE_MIRROR_URL}/cassandra_${cassandra_version}_all.deb" &&
      curl -SLO "${C_APACHE_MIRROR_URL}/cassandra-tools_${cassandra_version}_all.deb")
fi

echo "HERE 1"

dagi dpkg-dev cpio libcap2-bin dnsutils

echo "HERE 2"

APT_GET_OPTS="--allow-unauthenticated" dagi file ${arch_pkg_dir}/cassandra_${cassandra_version}_all.deb
APT_GET_OPTS="--allow-unauthenticated" dagi file ${arch_pkg_dir}/cassandra-tools_${cassandra_version}_all.deb
APT_GET_OPTS="--allow-unauthenticated" dagi vim

echo "HERE 3"

# package "cleanup"
mkdir /usr/share/cassandra/agents
# without version, it varies from 3.x to 4.x
mv /usr/share/cassandra/lib/jamm-*.jar /usr/share/cassandra/agents/jamm.jar
cp /etc/cassandra/hotspot_compiler /usr/share/cassandra/
cp /etc/cassandra/cassandra.yaml /usr/share/cassandra/

# nuke contents of /etc/cassandra and /var/lib/cassandra since they're injected by volume mounts
rm -rf /etc/cassandra/* /var/lib/cassandra/*

# add image config .d directories
mkdir /etc/cassandra/cassandra.yaml.d
mkdir /etc/cassandra/cassandra-env.sh.d
mkdir /etc/cassandra/jvm.options.d
mkdir /etc/cassandra/logback.xml.d

if [ "$INSTALL_CASSANDRA_EXPORTER" == "true" ]
then
    # install cassandra-exporter (Prometheus monitoring support)
    (cd "/usr/share/cassandra/agents" &&
        wget "https://github.com/instaclustr/cassandra-exporter/releases/download/v${cassandra_exporter_version}/cassandra-exporter-agent-${cassandra_exporter_version}.jar" &&
        ln -s cassandra-exporter-agent-${cassandra_exporter_version}.jar cassandra-exporter-agent.jar)
fi

# clean-up
rm -rf "${pkg_dir}"
apt-get -y remove dpkg-dev && apt-get -y autoremove

rm "${BASH_SOURCE}"
rm -rf /etc/apt/sources.list.d/cassandra.sources.list
