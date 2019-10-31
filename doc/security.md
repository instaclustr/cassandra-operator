## Securing the Cassandra cluster

## Requirements
- A running Kubernetes cluster setup with [Persistent Volumes][storage]. We advise running 1 Cassandra pod per k8 node, and hence please plan your environment accordingly.
> Note: See [AKS](./providers/aks.md) notes to properly define parameters for clusters running on AKS.

> Note: See [Dynamic provisioning](persistent_volumes.md) for notes to setup the persistent volumes properly for dynamic provisioning. 

> Note: See [Local setup](./providers/local.md) for notes on setting up a local Kubernetes cluster

- A service account used for running the Cassandra clusters with permissions to create, list and get certificate signature requests in the Kubernetes.

- A manual or automatic approval of the CSR objects in the Kubernetes cluster.

### Configuration

In order to setup the usage of the build-in certificates (generated per service account), use `secureCluster` option to CRD:

```json
...
secureCluster: true
...
```

and create the cluster. Don't forget to monitor the CSR objects (i.e with `kubectl get csr -w`) and approve the requests as they appear. Make sure you only approve relevant requests - the `Requestor` for Cassandra ones will be the appropriate service account that you run the clusters with:

```bash
[0] % kubectl get csr                   
NAME                                    AGE   REQUESTOR          CONDITION
cassandra-test-dc-cassandra-west1-a-0   60s   cassandra           Pending
``` 

To manually approve a specific request use `kubectl certificate approve` command:

```bash
[0] % kubectl certificate approve cassandra-test-dc-cassandra-west1-a-0
certificatesigningrequest.certificates.k8s.io/cassandra-test-dc-cassandra-west1-a-0 approved
```

To reiterate: approve the certificates one by one, as they appear. After all CSRs are approved, the cluster will start in a secure mode.

[storage]: https://kubernetes.io/docs/concepts/storage/persistent-volumes/
