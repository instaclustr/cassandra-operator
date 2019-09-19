## Custom Cassandra Configurations

The bundled Cassandra docker image includes a slightly customised Cassandra configuration that better suited for running inside a container,
but leaves the bulk of the configuration up to cassandra-operator.

cassandra-operator automatically configures Cassandra and the JVM with (what we consider) sane defaults for the deployment,
such as adjusting JVM heap sizes and GC strategies to values appropriate for the container resource limits.

That said, different workloads require tuning the configuration to achieve best performance, and this tuning cannot be achieved automatically by the operator.
Hence custom user configuration overrides are also supported.


### Rack Awareness
The operator supports rack aware Cassandra deployments and will automatically configure and manage a separate StatefulSet
per each of the defined racks. You can also assign node placement labels to each rack, so you can leverage kubernetes fault domains or other placement
strategies. To define racks, modify the racks object in the CRD. The operator will automatically balance replicas across your racks.

> In case you do not specify any racks, all replicas will be placed to same "rack" called `rack1` and there will be no balancing done for placing the pods.

Example yaml fragment:

```yaml
  racks:
    - name: "west1-b"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-b
    - name: "west1-c"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-c
    - name: "west1-a"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-a
```

See the full example yaml [here](../examples/example-datacenter.yaml)

### Cassandra.yaml and cassandra-env.sh

cassandra-operator supports mounting a custom [ConfigMap](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#configmap-v1-core)
(via a [ConfigMapVolumeSource](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#configmapvolumesource-v1-core)) into the Cassandra container.
The contents of this ConfigMap will be overlaid on top the images' out-of-box defaults and operator-generated configuration.

More specifically, all Cassandra and JVM configuration exists under `/etc/cassandra` inside the container (see [DirectoryLayout](directory_layout.md)).
The specified ConfigMap volume will have its contents extracted into `/etc/cassandra` when the container starts, allowing customisation
to the Cassandra configuration by either adding file fragments to specific directories (preferred, see below)
or by overwriting existing files entirely.

To customize the Cassandra configuration first create a ConfigMap object in the same K8s namespace as the Cassandra
deployment, and fill it with the required configuration, where the `data` keys are arbitrary (they are referenced by the
ConfigMapVolumeSource) and the values are the configuration files or file fragments.

Then set/update the CRD attribute `userConfigMapVolumeSource` to a ConfigMapVolumeSource object that
defines the ConfigMap key -> path mappings.

####Example:

Let's say you want to modify the number of threads available for certain operations in Cassandra.
Create a YAML file `100-concurrent.yaml` that just defines those properties:

```yaml
concurrent_reads: 12
concurrent_writes: 12
concurrent_counter_writes: 12
``` 

Create a config map from that YAML file:

```
$ kubectl create configmap concurrent-data --from-file=100-concurrent.yaml
```

Modify the CassandraDataCenter CRD to reference the newly created ConfigMap:

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: example-cdc
  ...
spec:
  ...

  userConfigMapVolumeSource:
    # the name of the ConfigMap
    name: concurrent-data
    # ConfigMap keys -> file paths (relative to /etc/cassandra)
    items:
      - key: 100-concurrent-yaml
        path: cassandra.yaml.d/100-concurrent.yaml
```

Cassandra will load the `cassandra.yaml.d/100-concurrent.yaml` file as well as the default settings managed by the operator!

