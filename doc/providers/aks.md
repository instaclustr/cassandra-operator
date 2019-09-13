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
spec:
  fsGroup: 999
EOF

kubectl apply -f cluster.yaml
```

>NOTE: Setting `fsGroup` to `999` is necessary on AKS. Without it, Azure Disk volumes attached to
>the Cassandra pods won't be writeable by the Cassandra process.

[aks]: https://azure.microsoft.com/en-in/services/kubernetes-service/
