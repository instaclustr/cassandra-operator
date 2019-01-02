## Installing the Cassandra operator
### Quick start installations
To build and install locally follow the below steps:
```bash
#in the project root directory e.g. cd ~/git/cassandra-operator/

#Start a minikube cluster with enough resources, cassandra is hungry!
minikube start --cpus 4 --memory 4096 --kubernetes-version v1.9.4

#Use the minikube docker context 
eval $(minikube docker-env)

#Build the operator
mvn -f java/pom.xml clean package

#Build the required docker images
./buildenv/build-all

#Create the operator using the default bundle
kubectl apply -f examples/common/rbac-bundle.yaml

#Check status
kubectl get pods --selector=k8s-app=cassandra-operator
```

Verify that you can create a Cassandra cluster
```bash
kubectl apply -f examples/common/test.yaml

#wait for all pods to be up (this may take some time)

kubectl get pods --selector=cassandra-datacenter=test-dc 

kubectl exec test-dc-2 -i -t -- bash -c 'cqlsh test-dc-seeds'
```

### Helm Instructions
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

The Helm templates are relatively independent and can also be used to generate the deployments yaml file offline:
```bash
helm template helm/cassandra-operator -n cassandra-operator
```

## Custom Namespace support
Custom namespace support is somewhat limited at the moment primarily due to laziness. You can have the operator watch a different namespace (other than "defaul") by changing the namepsace it watches on startup. This is not an optimal way to do so (we should watch all namespaces... maybe), but it's the implementation as it stands.

To changes the namespace the operator watches (it can be deployed in a different namespace if you want), you will need to modify the deployment the operator gets deployed by (either the helm package or the example yaml) to include the following in the containers spec:

```yaml
command: ["java"]
args: ["-jar", "/opt/cassandra-operator/operator-1.0.jar", "--namespace=NAMESPACE"]
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
          args: ["-jar", "/opt/cassandra-operator/operator-1.0.jar", "--namespace=NAMESPACE"]
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
