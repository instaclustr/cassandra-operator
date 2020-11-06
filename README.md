## Cassandra operator
Build [![CircleCI](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master.svg?style=svg)](https://circleci.com/gh/instaclustr/cassandra-operator/tree/master)

### Project status: alpha
The Cassandra operator manages Cassandra clusters deployed to [Kubernetes](http://kubernetes.io) and automates tasks related to operating a Cassandra cluster.

Currently user facing Cassandra cluster objects are created as [Kubernetes Custom Resources](https://kubernetes.io/docs/tasks/access-kubernetes-api/extend-api-custom-resource-definitions/).

### Cassandra version support

We are currently building images for Cassandra 3.11.9 and 4.0-beta3. 

## Documentation

All relevant information related to the usage our Instaclustr Cassandra operator is in our [operator wiki](https://github.com/instaclustr/cassandra-operator/wiki)

## Requirements

- Kubernetes 1.15+
- Helm 3 (if you want to use the included helm charts)

## Limitations

- This operator is currently a work in progress and breaking changes are landing in master all the time. Here be dragons!
- We expect to consider the Cassandra operator stable soon; backwards incompatible changes will not be made once the project reaches stability.
- Some planned features have yet to be completed and API changes are still possible, meaning that bug fixes, API and version changes may not be backwards compatible.
- Please see the [Instaclustr supported projects page](https://www.instaclustr.com/support/documentation/announcements/instaclustr-open-source-project-status/) for the Enterprise support status of the Cassandra Operator.