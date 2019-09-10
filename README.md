## Cassandra operator
This project is being rewritten in Go to leverage the operator framework and leverage a better kubernetes client. See this [branch](https://github.com/instaclustr/cassandra-operator/tree/superdupertopsecretrewrite). 

### Project status: alpha

Major planned features have yet to be completed and API changes are currently planned, we reserve the right to address bugs and API changes in a backwards incompatible way before the project is declared stable. See [upgrade guide](./doc/user/upgrade/upgrade_guide.md) for safe upgrade process.

Currently user facing Cassandra cluster objects are created as [Kubernetes Custom Resources](https://kubernetes.io/docs/tasks/access-kubernetes-api/extend-api-custom-resource-definitions/).

We expect to consider the Cassandra operator stable soon; backwards incompatible changes will not be made once the project reaches stability.

## Overview

The Cassandra operator manages Cassandra clusters deployed to [Kubernetes](http://kubernetes.io) and automates tasks related to operating an Cassandra cluster.

- [Install](./doc/op_guide.md)
- [Create and destroy](#create-and-destroy-an-Cassandra-cluster)
- [Resize](#resize-an-Cassandra-cluster)
- [Rolling upgrade](#upgrade-an-Cassandra-cluster) - _TODO_
- [Limitations](#limitations)

## Documentation

1) [Operations Guide](./doc/op_guide.md) for how to run the operator and deploy Cassandra clusters.

2) [Best Practices](./doc/best_practices.md) for more information on how to better use Cassandra operator.

3) [RBAC docs](./doc/rbac.md) for how to setup RBAC rules for Cassandra operator if RBAC is in place.

4) [Developer Guide](./doc/developers.md) for setting up development environment if you want to contribute.

5) [Backup and Restore Guide](./doc/backup_restore.md) for backing up and restoring Cassandra clusters managed by the operator.

6) [Resources and Labels](./doc/resources.md) doc for an overview of the resources created by the Cassandra-operator.

7) [Specific examples](./doc/spec_examples.md) on setting up clusters with backup, restore, and other configurations.

8) [PKS Setup](./doc/providers/pks.md) for deploying on Pivotal Container service. 

## Requirements

- Kubernetes 1.13+
- Cassandra 3.11+

## Deploy Cassandra operator

See [instructions on how to install/uninstall Cassandra operator](./doc/op_guide.md) .

## Limitations

- This operator is currently a work in progress and breaking changes are landing in master all the time until we reach our initial release. Here be dragons!
- Do not use this in production... yet
- Please see the [Instaclustr supported projects page](https://www.instaclustr.com/support/documentation/announcements/instaclustr-open-source-project-status/) for the Enterprise support status of the Cassandra Operator.
