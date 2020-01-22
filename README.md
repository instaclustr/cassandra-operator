## Cassandra operator
Build [![CircleCI](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master.svg?style=svg)](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master)

### Project status: alpha
The Cassandra operator manages Cassandra clusters deployed to [Kubernetes](http://kubernetes.io) and automates tasks related to operating a Cassandra cluster.

Some planned features have yet to be completed and API changes are still possible, meaning that bug fixes, API and version changes may not be backwards compatible.

Currently user facing Cassandra cluster objects are created as [Kubernetes Custom Resources](https://kubernetes.io/docs/tasks/access-kubernetes-api/extend-api-custom-resource-definitions/).

We expect to consider the Cassandra operator stable soon; backwards incompatible changes will not be made once the project reaches stability.

## Documentation

1) [Operations Guide](./doc/op_guide.md) for how to run the operator and deploy, scale and decommission Cassandra clusters.

1) [Custom configuration](./doc/custom-configuration.md) how to configure Cassandra itself

1) [RBAC docs](./doc/rbac.md) for how to setup RBAC rules for Cassandra operator and Cassandra containers if RBAC is in place.

1) [Developer Guide](./doc/developers.md) for setting up development environment if you want to contribute.

1) [Backup and Restore Guide](./doc/backup_restore.md) for backing up and restoring Cassandra clusters managed by the operator.

1) [Resources and Labels](./doc/resources.md) documentation for an overview of the resources created by the Cassandra-operator.

1) [Service accounts, login and SSL](./doc/auth.md) documentation for authentication and how you can log in to cluster

1) Using [Helm](./doc/helm.md) to install and launch the operator and the clusters.

## Requirements

- Kubernetes 1.13+
- Helm 2 (if you want to use the included helm charts)

## Limitations

- This operator is currently a work in progress and breaking changes are landing in master all the time. Here be dragons!
- Do not use this in production... yet
- Please see the [Instaclustr supported projects page](https://www.instaclustr.com/support/documentation/announcements/instaclustr-open-source-project-status/) for the Enterprise support status of the Cassandra Operator.