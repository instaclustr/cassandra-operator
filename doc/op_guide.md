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

## Changing Cassandra configuration
You can change and override the default Cassandra and Operator configuration using your own config maps and YAML fragments.
The operator uses a custom configuration loader for Cassandra that will find multiple yaml files in a set of
specified folders, concatenate them together then set as the Cassandra config. This allows you to override or change existing Cassandra config.
You can also use the user config map to inject files like keystores into the pod for encryption purposes.

Let's say you want to modify the number of threads available for certain operations in Cassandra. You can create a yaml file (concurrent.yaml) that just defines those properties:

```yaml
concurrent_reads: 12
concurrent_writes: 12
concurrent_counter_writes: 12
``` 

Create a config map from that yaml file `kubectl create configmap concurrent-data --from-file concurrent.yaml`.

Modify the CassandraDataCenter CRD to reference the newly created config map:

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: config-test4-cassandra
  labels:
    app: cassandra
    chart: cassandra-0.1.0
    release: config-test4
    heritage: Tiller
spec:
  replicas: 3
  cassandraImage: "gcr.io/cassandra-operator/cassandra-dev:latest"
  sidecarImage: "gcr.io/cassandra-operator/cassandra-sidecar-dev:latest"
  imagePullPolicy: IfNotPresent
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

  userConfigMap: concurrent-data
```

Cassandra will load the concurrent.yaml file as well as its default settings managed by the operator!
