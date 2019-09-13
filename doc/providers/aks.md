# Cassandra Operator on Azure Kubernetes Service

This guide explains how to deploy and use the Cassandra Operator on [Azure Kubernetes Service (AKS)][aks].

## Requirements

- An AKS cluster with at least 3 nodes.

## Deploy the Operator

Deploy the [CRDs][crds] used by the operator to manage Cassandra clusters:

```
kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandrabackup_crd.yaml
kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandracluster_crd.yaml
kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandradatacenter_crd.yaml
```

Set the operator's Docker image in `deploy/operator.yaml`:

>TODO: Commit the official image in the manifest so that the user doesn't have to edit files.

```
sed -i 's|REPLACE_IMAGE|quay.io/<user>/cassandra-operator:latest|' deploy/operator.yaml
```

Deploy the [RBAC][rbac] resources and [pod security policies][psps] used by the operator to create
Cassandra pods:

>TODO: Remove this? This isn't mandatory on AKS as things work fine with the `default` SA.

```
kubectl apply -f deploy/cassandra
```

Deploy the operator itself:

```
kubectl apply -f deploy
```

Verify the operator is running:

```
kubectl get pods | grep cassandra-operator
```

```
cassandra-operator-5755f6855f-t9hvm   1/1     Running   0          65s
```

## Deploy a Cassandra Cluster

Deploy a sample Cassandra cluster:

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

Wait for the pods to become ready:

```
kubectl get pods | grep cassandra-test
```

```
cassandra-cassandra-test-rack1-0      2/2     Running   0          50m
cassandra-cassandra-test-rack1-1      2/2     Running   0          47m
cassandra-cassandra-test-rack1-2      2/2     Running   0          45m
```

>NOTE: It could take a few minutes for the pods to converge while Azure Disks are being
>automatically provisioned and attached to the cluster nodes.

Verify the Cassandra cluster is healthy:

```
kubectl exec cassandra-cassandra-test-rack1-0 -c cassandra -- nodetool -h cassandra-cassandra-test-nodes status
```

```
Datacenter: cassandra-test
==========================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address      Load       Tokens       Owns (effective)  Host ID                               Rack
UN  10.244.1.14  87.41 KiB  256          62.9%             dcf940c2-18d2-4a3a-8abf-833acadeca7e  rack1
UN  10.244.2.9   87.38 KiB  256          69.6%             fd59fa32-aab0-485e-b04b-7ad4b75e54dd  rack1
UN  10.244.0.10  69.91 KiB  256          67.5%             9e4883a1-e822-472f-920f-f2fc36c340c8  rack1
```

Issue a sample query to the cluster:

```
kubectl exec cassandra-cassandra-test-rack1-0 -c cassandra -- cqlsh -e "SELECT now() FROM system.local;" cassandra-cassandra-test-nodes
```

```
 system.now()
--------------------------------------
 243e2fd0-d64a-11e9-b8a4-2dd801fa1b1c

(1 rows)
```

## Cleanup

>**WARNING! The following will delete the Cassandra cluster deployed in the previous steps as well
>as all of its data.**

### Delete the Cassandra Cluster

Delete the Cassandra cluster:

```
kubectl delete -f cluster.yaml
```

Delete the PVCs created automatically for the pods:

```
kubectl delete pvc data-volume-cassandra-cassandra-test-rack1-{0..2}
```

Remove the sample manifest:

```
rm cluster.yaml
```

### Delete the Operator

Delete the operator:

```
kubectl delete -f deploy
```

Delete the RBAC and PSP resources:

```
kubectl delete -f deploy/cassandra
```

Delete the CRDs:

```
kubectl delete -f deploy/crds/cassandraoperator_v1alpha1_cassandrabackup_crd.yaml
kubectl delete -f deploy/crds/cassandraoperator_v1alpha1_cassandracluster_crd.yaml
kubectl delete -f deploy/crds/cassandraoperator_v1alpha1_cassandradatacenter_crd.yaml
```

[aks]: https://azure.microsoft.com/en-in/services/kubernetes-service/
[crds]: https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions
[psps]: https://kubernetes.io/docs/concepts/policy/pod-security-policy/
[rbac]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
