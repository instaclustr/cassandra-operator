##Installing the Cassandra operator
### Quick start installations
To build and install locally follow the below steps:
```bash
#in the project root directory e.g. cd ~/git/cassandra-operator/

#Start a minikube cluster with enough resources, cassandra is hungry!
minikube start --cpus 4 --memory 4096 --vm-driver hyperkit --kubernetes-version v1.9.4

#Use the minikube docker context 
eval $(minikube docker-env)

#Build the operator
mvn clean package

#Build the required docker images
./build/build-all

#Create the operator using the default bundle
kubectl apply -f examples/common/bundle.yaml

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


