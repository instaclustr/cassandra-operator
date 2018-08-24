#!/bin/bash -xue

# overlay configuration from configmap mounted volumes into /etc/cassandra
(
for config_directory in "$@"
do
    # k8s configmap volumes are a mess of symlinks -- the find command cleans this up (skip ay dirs starting with ..)

    cd "${config_directory}"
    find -L . -name "..*" -prune -o \( -type f -print0 \) |
        cpio -pmdv0 /etc/cassandra
done
)

/usr/sbin/cassandra || true

sleep 10000
