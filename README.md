## Cassandra operator
Build [![CircleCI](https://circleci.com/gh/benbromhead/cassandra-operator/tree/master.svg?style=svg)](https://circleci.com/gh/benbromhead/cassandra-operator/tree/master)
Test 

### Project status: alpha

Major planned features have yet to be completed and API changes are currently planned, we reserve the right to address bugs and API changes in a backwards incompatible way before the project is declared stable. See [upgrade guide](./doc/user/upgrade/upgrade_guide.md) for safe upgrade process.

Currently user facing Cassandra cluster objects are created as [Kubernetes Custom Resources](https://kubernetes.io/docs/tasks/access-kubernetes-api/extend-api-custom-resource-definitions/), however, taking advantage of [User Aggregated API Servers](https://github.com/kubernetes/community/blob/master/contributors/design-proposals/api-machinery/aggregated-api-servers.md) to improve reliability, validation and versioning may be undertaken. The use of Aggregated API should be minimally disruptive to existing users but may change what Kubernetes objects are created or how users deploy the  operator.

We expect to consider the Cassandra operator stable soon; backwards incompatible changes will not be made once the project reaches stability.

## Overview

The Cassandra operator manages Cassandra clusters deployed to [Kubernetes][k8s-home] and automates tasks related to operating an Cassandra cluster.

- [Create and destroy](#create-and-destroy-an-Cassandra-cluster)
- [Resize](#resize-an-Cassandra-cluster)
- [Recover a member](#member-recovery)
- [Backup and restore a cluster](#disaster-recovery)
- [Rolling upgrade](#upgrade-an-Cassandra-cluster)

There are [more spec examples](./doc/user/spec_examples.md) on setting up clusters with backup, restore, and other configurations.

Read [Best Practices](./doc/best_practices.md) for more information on how to better use Cassandra operator.

Read [RBAC docs](./doc/user/rbac.md) for how to setup RBAC rules for Cassandra operator if RBAC is in place.

Read [Developer Guide](./doc/dev/developer_guide.md) for setting up development environment if you want to contribute.

See the [Resources and Labels](./doc/user/resource_labels.md) doc for an overview of the resources created by the Cassandra-operator.

## Requirements

- Kubernetes 1.7+
- Cassandra 3.11+

## Deploy Cassandra operator

See [instructions on how to install/uninstall Cassandra operator](doc/user/op_guide.md) .

## Create and destroy an Cassandra cluster

```bash
$ kubectl create -f example/example-cassandra-cluster.yaml
```

A 3 member Cassandra cluster will be created.

```bash
$ kubectl get pods
NAME                            READY     STATUS    RESTARTS   AGE
example-cassandra-cluster-0000       1/1       Running   0          1m
example-cassandra-cluster-0001       1/1       Running   0          1m
example-cassandra-cluster-0002       1/1       Running   0          1m
```

See [client service](doc/user/client_service.md) for how to access Cassandra clusters created by operator.

Destroy Cassandra cluster:

```bash
$ kubectl delete -f example/example-Cassandra-cluster.yaml
```

## Resize an Cassandra cluster

Create an Cassandra cluster:

```
$ kubectl apply -f example/example-Cassandra-cluster.yaml
```

In `example/example-Cassandra-cluster.yaml` the initial cluster size is 3.
Modify the file and change `size` from 3 to 5.

```
$ cat example/example-Cassandra-cluster.yaml
apiVersion: "cassandra.database.instaclustr.com/v1beta2"
kind: "CassandraCluster"
metadata:
  name: "example-cassandra-cluster"
spec:
  size: 5
  version: "3.11.0"
```

Apply the size change to the cluster CR:
```
$ kubectl apply -f example/example-cassandra-cluster.yaml
```
The Cassandra cluster will scale to 5 members (5 pods):
```
$ kubectl get pods
NAME                            READY     STATUS    RESTARTS   AGE
example-cassandra-cluster-0000       1/1       Running   0          1m
example-cassandra-cluster-0001       1/1       Running   0          1m
example-cassandra-cluster-0002       1/1       Running   0          1m
example-cassandra-cluster-0003       1/1       Running   0          1m
example-cassandra-cluster-0004       1/1       Running   0          1m
```

Similarly we can decrease the size of cluster from 5 back to 3 by changing the size field again and reapplying the change.

```
$ cat example/example-Cassandra-cluster.yaml
apiVersion: "cassandra.database.instaclustr.com/v1beta2"
kind: "cassandraCluster"
metadata:
  name: "example-cassandra-cluster"
spec:
  size: 3
  version: "3.11.0"
```
```
$ kubectl apply -f example/example-cassandra-cluster.yaml
```

We should see that Cassandra cluster will eventually reduce to 3 pods:

```
$ kubectl get pods
NAME                            READY     STATUS    RESTARTS   AGE
example-cassandra-cluster-0002       1/1       Running   0          1m
example-cassandra-cluster-0003       1/1       Running   0          1m
example-cassandra-cluster-0004       1/1       Running   0          1m
```

## Limitations

- The Cassandra operator only manages the Cassandra cluster created in the same namespace. Users need to create multiple operators in different namespaces to manage Cassandra clusters in different namespaces.
- PV Backup only works on GCE(kubernetes.io/gce-pd) and AWS(kubernetes.io/aws-ebs) for now.


[k8s-home]: http://kubernetes.io
