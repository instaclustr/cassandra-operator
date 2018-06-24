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
cd java
mvn clean package
cd -

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
helm install helm/cassandra-operator -n RELEASE_NAME

#Create a Cassandra cluster
#Remember to check the values.yaml file e.g. vim helm/cassandra/values.yaml
helm install helm/cassandra -n CASSANDRA_CLUSTER_NAME
```

The Helm templates are relatively independent and can also be used to generate the deployments yaml file offline:
```bash
helm template helm/cassandra-operator -n RELEASE_NAME

```
