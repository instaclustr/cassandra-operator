## Installing the Cassandra operator on PKS
### Prerequisites
1) Ensure you have configured kubectl to point to your PKS cluster:
    ```bash
    pks login -a PKS-API -u USERNAME -k
    pks get-credentials CLUSTER-NAME
    ```
    `pks` will automatically set your kubectl context so you can now interact with your cluster via standard tooling.

2) (Optional) Ensure Helm and Tiller is configured and installed in your PKS deployment. Non-Tiller (the cluster side component of Helm) instructions are listed below. 
See Pivotals documentation for installing [Helm on PKS](https://docs.pivotal.io/runtimes/pks/1-2/helm.html) . 

3) Ensure you have configured Persistant Volumes for your PKS cluster. 
See Pivotals documentation for [configuring dynamic PVs](https://docs.pivotal.io/runtimes/pks/1-2/configuring-pvs.html). 


### Installing the operator with Helm and Tiller
The project include in-tree helm templates to make installation simpler and repeatable. 
To install via helm follow the steps below:

```bash
#First check and change the values.yaml file to make sure it meets your requirements:
#e.g. vim helm/cassandra-operator/values.yaml

#Install the operator
helm install helm/cassandra-operator -n cassandra-operator

#Create a Cassandra cluster
#Remember to check the values.yaml file e.g. vim helm/cassandra/values.yaml
helm install helm/cassandra -n test-cluster
```

### Installing the operator without Tiller
The Helm templates are relatively independent and can also be used to generate the deployments yaml file offline:
```bash
helm template helm/cassandra-operator -n cassandra-operator > operator.yaml
```

Once you have generated the yaml file locally using Helm, you can deploy to PKS via kubectl:
```bash
kubectl apply -f operator.yaml
```

You can also build the yaml by hand, refer to the examples directory and the helm templates for requirements. 

## Custom Namespace support
Custom namespace support is somewhat limited at the moment primarily due to laziness. You can have the operator watch a different namespace (other than "defaul") by changing the namepsace it watches on startup. This is not an optimal way to do so (we should watch all namespaces... maybe), but it's the implementation as it stands.

To changes the namespace the operator watches (it can be deployed in a different namespace if you want), you will need to modify the deployment the operator gets deployed by (either the helm package or the example yaml) to include the following in the containers spec:

```yaml
command: ["java"]
args: ["-jar", "/opt/lib/cassandra-operator/cassandra-operator.jar", "--namespace=NAMESPACE"]
```

The modified helm package (helm/cassandra-operator/templates/deployment.yaml) would look like:
```yaml
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  labels:
    app: {{ template "cassandra-operator.name" . }}
    chart: {{ .Chart.Name }}-{{ .Chart.Version }}
    heritage: {{ .Release.Service }}
    operator: cassandra
    release: {{ .Release.Name }}
  name: {{ template "cassandra-operator.fullname" . }}
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: {{ template "cassandra-operator.name" . }}
        operator: cassandra
        release: {{ .Release.Name }}
    spec:
      containers:
        - name: {{ template "cassandra-operator.name" . }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: "{{ .Values.image.pullPolicy }}"
          command: ["java"]
          args: ["-jar", "/opt/lib/cassandra-operator/cassandra-operator.jar", "--namespace=NAMESPACE"]
          ports:
            - containerPort: 8080
              name: http
          resources:
{{ toYaml .Values.resources | indent 12 }}
    {{- if .Values.nodeSelector }}
      nodeSelector:
{{ toYaml .Values.nodeSelector | indent 8 }}
    {{- end }}
    {{- if .Values.rbacEnable }}
      serviceAccountName: {{ template "cassandra-operator.fullname" . }}
    {{- end }}
    {{- if .Values.tolerations }}
      tolerations:
{{ toYaml .Values.tolerations | indent 8 }}
      securityContext:
{{ toYaml .Values.securityContext | indent 8 }}
{{- end }}

```
## Deploying Cassandra
You can now deploy Cassandra by using the in Helm package (helm/cassandra) or my creating your own CDC object (see examples directory).
Key configuration components are defined in a values.yaml file. You can either use the default or define your own (see example below):


```yaml
# Default values for cassandra.
# This is a YAML-formatted file.
# Declare variables to be passed into your templates.

replicaCount: 3

image:
  cassandraRepository: cassandra
  sidecarRepository: cassandra-sidecar
  cassandraTag: 3.11.3
  sidecarTag: latest

imagePullPolicy: IfNotPresent
imagePullSecret: ""

privilegedSupported: false

resources:
  limits:
    memory: 512Mi
  requests:
    memory: 512Mi
dataVolumeClaim:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Mi
  storageClassName: fast # note this may not be applicable in your environment


prometheusEnabled: false
```

You can also use a client side `helm template` approach as described above. 
```bash
helm template helm/cassandra -n cassandra-cluster> cluster.yaml
```

## PKS Specific configuration
The following values may need to be modified to run Cassandra on PKS:
1) `spec.privilegedSupported` - PKS does not allow privileged containers to be run by default. This will cause the init container to error out.
Set to `false`


## Cassandra Configuration

The bundled Cassandra docker image includes a slightly customised Cassandra configuration that better suited for running inside a container,
but leaves the bulk of the configuration up to cassandra-operator.

cassandra-operator automatically configures Cassandra and the JVM with (what we consider) sane defaults for the deployment,
such as adjusting JVM heap sizes and GC strategies to values appropriate for the container resource limits.

That said, different workloads require tuning the configuration to achieve best performance, and this tuning cannot be achieved automatically by the operator.
Hence custom user configuration overrides are also supported.


### Basics

cassandra-operator supports mounting a custom [ConfigMap](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#configmap-v1-core)
(via a [ConfigMapVolumeSource](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#configmapvolumesource-v1-core)) into the Cassandra container.
The contents of this ConfigMap will be overlaid on top the images' out-of-box defaults and operator-generated configuration.

More specifically, all Cassandra and JVM configuration exists under `/etc/cassandra` inside the container.
The specified ConfigMap volume will have its contents extracted into `/etc/cassandra` on container start allowing customisation
to the Cassandra configuration by either adding file fragments to specific directories (preferred, see below)
or by overwriting existing files entirely.

To customize the Cassandra configuration first create a ConfigMap object in the same K8s namespace as the Cassandra
deployment, and fill it with the required configuration, where the `data` keys are arbitrary (they are referenced by the
ConfigMapVolumeSource) and the values are the configuration files or file fragments.

Then set/update the CassandraDataCenter attribute `userConfigMapVolumeSource` to a ConfigMapVolumeSource object that
defines the ConfigMap key -> path mappings.


### Fragment Files

#### `cassandra.yaml.d/`
Contains YAML fragment files (`.yaml`) that will be loaded by Cassandra on startup in lexicographical order.
These fragments are loaded after the main `cassandra.yaml` file.

These fragments may override existing settings set in the main `cassandra.yaml` file, or settings defined in any previous fragments.

Use cases include:

* Setting default compaction throughput
* Enabling authentication/authorization
* Tuning thread pool sizes

#### `cassandra-env.sh.d/`
Contains bash shell script fragment files (`.sh`) that will be sourced (in lexicographical order) during Cassandra
startup from the main `cassandra.sh` startup script.

These scripts may perform a number of operations, including modifying variables such as `CASSANDRA_CLASSPATH` or
`JVM_OPTS`, though for the latter prefer to use `.options` fragments (see below) unless shell evaluation/expansion is required.

#### `jvm.options.d/`
Contains text fragment files (`.options`) that will be parsed (in lexicographical order) during Cassandra startup
from the main `cassandra.sh` startup script file to construct the JVM command line parameters.

These fragment files are parsed identically to the main `jvm.options` file -- lines not staring with a `-` are ignored.
All other lines are assumed to define command line arguments for the JVM.

Use cases include:
* Tuning JVM CG settings
* Configuring GC logging


### Examples

Let's say you want to modify the number of threads available for certain operations in Cassandra.
Create a YAML file (`100-concurrent.yaml`) that just defines those properties:

```yaml
concurrent_reads: 12
concurrent_writes: 12
concurrent_counter_writes: 12
``` 

Create a config map from that YAML file:

```$ kubectl create configmap concurrent-data --from-file 100-concurrent.yaml```.


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
