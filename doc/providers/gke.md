# Cassandra Operator on Google Kubernetes Environment

This guide explains how to deploy and use the Cassandra Operator on [Google Kubernetes Environment][gke].

## Requirements

- A GKE cluster with at least 3 nodes. If Cassandra's rack awareness is important in your case, deploy your environment with nodes in different fault domains (zones).
- A storage type that allows creating PVs in the same zone as the requesting pod/claim. To create such storage type, use the following example for `topology-aware-standard` as a template:
    ```yaml
    kind: StorageClass
    apiVersion: storage.k8s.io/v1
    metadata:
      name: topology-aware-standard
    provisioner: kubernetes.io/gce-pd
    volumeBindingMode: WaitForFirstConsumer
    parameters:
      type: pd-standard
    ```

    The most important element in the example is the `volumeBindingMode`, which has to be set to `WaitForFirstConsumer` for the storage you're using. After creating this storage type, use it in the storage spec for the CRD:
    ```yaml
    ...
    dataVolumeClaimSpec:
        accessModes:
          - ReadWriteOnce
        storageClassName: topology-aware-standard
        resources:
          requests:
            storage: 500Mi
    ...
    ```
## Deploy the Operator

Refer to [Operation Guide](../op_guide.md) to install and launch the operation in your k8 environment.

### Custom Cassandra configurations

Refer to [Custom configurations](custom-configuration.md) for options to configure your cluster

## Deploy a Cassandra Cluster

Refer to [Running a cluster](../run_cluster.md) to run the cluster in your environment.

[gke]: https://console.cloud.google.com/kubernetes
[crds]: https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions
[psps]: https://kubernetes.io/docs/concepts/policy/pod-security-policy/
[rbac]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
