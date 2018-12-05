## Cassandra operator
Build [![CircleCI](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master.svg?style=svg)](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master)

### Project status: alpha

Major planned features have yet to be completed and API changes are currently planned, we reserve the right to address bugs and API changes in a backwards incompatible way before the project is declared stable. See [upgrade guide](./doc/user/upgrade/upgrade_guide.md) for safe upgrade process.

Currently user facing Cassandra cluster objects are created as [Kubernetes Custom Resources](https://kubernetes.io/docs/tasks/access-kubernetes-api/extend-api-custom-resource-definitions/), however, taking advantage of [User Aggregated API Servers](https://github.com/kubernetes/community/blob/master/contributors/design-proposals/api-machinery/aggregated-api-servers.md) to improve reliability, validation and versioning may be undertaken. The use of Aggregated API should be minimally disruptive to existing users but may change what Kubernetes objects are created or how users deploy the  operator.

We expect to consider the Cassandra operator stable soon; backwards incompatible changes will not be made once the project reaches stability.

## Overview

The Cassandra operator manages Cassandra clusters deployed to [Kubernetes](http://kubernetes.io) and automates tasks related to operating an Cassandra cluster.

- [Install](#deploy-cassandra-operator)
- [Create and destroy](#create-and-destroy-an-Cassandra-cluster)
- [Resize](#resize-an-Cassandra-cluster)
- [Rolling upgrade](#upgrade-an-Cassandra-cluster) - _TODO_
- [Limitations](#limitations)

There are [more spec examples](./doc/spec_examples.md) on setting up clusters with backup, restore, and other configurations.

Read [Best Practices](./doc/best_practices.md) for more information on how to better use Cassandra operator.

Read [RBAC docs](./doc/rbac.md) for how to setup RBAC rules for Cassandra operator if RBAC is in place.

Read [Developer Guide](./doc/developers.md) for setting up development environment if you want to contribute.

Read [Backup and Restore Guide](./doc/backup_restore.md) for backing up and restoring Cassandra clusters managed by the operator.

See the [Resources and Labels](./doc/resources.md) doc for an overview of the resources created by the Cassandra-operator.

## Requirements

- Kubernetes 1.8+
- Cassandra 3.11+

## Deploy Cassandra operator

See [instructions on how to install/uninstall Cassandra operator](./doc/op_guide.md) .

## Create and destroy an Cassandra cluster

```bash
$ kubectl create -f example/common/test.yaml
```

A 3 member Cassandra cluster will be created.

```bash
$ kubectl get pods
NAME                                    READY     STATUS    RESTARTS   AGE
cassandra-operator7-5d58bc7874-t85dt    1/1       Running   0          18h
test-dc-0                               1/1       Running   0          1m
test-dc-1                               1/1       Running   0          1m
test-dc-2                               1/1       Running   0          1m
```

See [client service](doc/user/client_service.md) for how to access Cassandra clusters created by operator.

Destroy Cassandra cluster:

```bash
$ kubectl delete -f example/common/test.yaml
```

## Resize an Cassandra cluster

Create an Cassandra cluster, if you haven't already:

```bash
$ kubectl apply -f example/common/test.yaml
```

In `example/common/test.yaml` the initial cluster size is 3.
Modify the file and change `replicas` from 3 to 5.

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: test-dc
spec:
  replicas: 5
  image: "gcr.io/cassandra-operator/cassandra:latest"
```

Apply the size change to the cluster CR:
```bash
$ kubectl apply -f example/common/test.yaml
```
The Cassandra cluster will scale to 5 members (5 pods):
```bash
$ kubectl get pods
NAME                            READY     STATUS    RESTARTS   AGE
test-dc-0                       1/1       Running   0          1m
test-dc-1                       1/1       Running   0          1m
test-dc-2                       1/1       Running   0          1m
test-dc-3                       1/1       Running   0          1m
test-dc-4                       1/1       Running   0          1m
```

Similarly we can decrease the size of cluster from 5 back to 3 by changing the size field again and reapplying the change.

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: test-dc
spec:
  replicas: 3
  image: "gcr.io/cassandra-operator/cassandra:latest"
```
Then apply the changes
```bash
$ kubectl apply -f example/common/test.yaml
```

## Limitations

- This operator is currently a work in progress and breaking changes are landing in master all the time until we reach our initial release. Here be dragons!
- Do not use this in production... yet
- Please see https://www.instaclustr.com/support/documentation/announcements/instaclustr-open-source-project-status/ for Instaclustr support status of this project
