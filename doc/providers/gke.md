## Setting up Persistent Volumes on GKE

### Prerequisites
- A GKE cluster (recommended with at least 3 nodes). If Cassandra's rack awareness is important in your case, deploy your environment with nodes in different fault domains ([zones][zones]).
- A storage class that allows creating PVs in the same zone as the requesting pod/claim. To create such storage class, use the following example for `topology-aware-standard` as a template:
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

    The most important element in the example is the `volumeBindingMode`, which has to be set to `WaitForFirstConsumer` for the storage you're using. After creating this storage class, use it in the storage spec for the CRD:
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
[zones]: https://kubernetes.io/docs/setup/best-practices/multiple-zones/