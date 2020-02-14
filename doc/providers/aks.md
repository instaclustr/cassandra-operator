# Cassandra Operator on Azure Kubernetes Service

This guide explains how to deploy and use the Cassandra Operator on [Azure Kubernetes Service (AKS)][aks].

## Requirements

- An AKS cluster with at least 3 nodes.

When deploying the cluster on AKS, it's important to properly set `fsGroup` option:
```
cat <<EOF >cluster.yaml
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraDataCenter
metadata:
  name: cassandra-test
...
spec:
  fsGroup: 999
EOF

kubectl apply -f cluster.yaml
```

>NOTE: Setting `fsGroup` to `999` is necessary on AKS. Without it, Azure Disk volumes attached to
>the Cassandra pods won't be writeable by the Cassandra process.

## Example of deployment

Firstly, you need to create a _resource group_ under which all resources will live:

```
$ az group create --name myResourceGroup --location eastus
```

Then we have to create AKS cluster, here with two nodes:

```
$ az aks create \
    --resource-group myResourceGroup 
    --name myAKSCluster \
    --node-count 2 \ 
    --generate-ssh-keys
```

The creation of a cluster takes a while. In order to connect to such cluster, 
you need to get credentials so your `kubectl` will work:

```
$ az aks get-credentials \
    --resource-group myResourceGroup \
    --name myAKSCluster
```

After you apply `cluster.yaml` above, you should have three nodes running:

```
$ kubectl get pods
NAME                                  READY   STATUS    RESTARTS   AGE
cassandra-cassandra-test-rack1-0      2/2     Running   6          79m
cassandra-cassandra-test-rack1-1      2/2     Running   0          75m
cassandra-cassandra-test-rack1-2      2/2     Running   0          73m
cassandra-operator-78c59b86cd-94jdq   1/1     Running   0          79m
```

If you want to expose a node, you need to do it like this:

```
$ kubectl expose pod cassandra-cassandra-test-rack1-0 \
    --type="LoadBalancer" \
    --name=node1-service \
    --port=9042 \
    --target-port=9042
```

After a while, you see that public IP was assigned, e.g. like this:

```
kubectl get services
NAME                TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)
... other services
node1-service       LoadBalancer   10.0.166.108   52.226.147.217   9042:30462/TCP
```

So you connect to that node like:

```
$ cqlsh 52.226.147.217
Connected to cassandra-test at 52.226.147.217:9042.
[cqlsh 5.0.1 | Cassandra 3.11.4 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cqlsh> 
```

Please keep in mind that this connection is not secured as it is not running on SSL. 
You might change authenticator via configuration mechanism beforehand to modify `cassandra.yaml` 
so you might create a user with strong password and possibly change password for default `cassandra` user. 