## Setting up Persistent Volumes for dynamic volume provisioning

### Prerequisites
- Any cluster (recommended with at least 3 nodes). If Cassandra's rack awareness is important in your case, deploy your environment with nodes in different fault domains ([zones][zones]).
- A storage class that allows creating PVs in the same zone as the requesting pod/pv claim (we name it `topology-aware-standard`, but use any name that you see fit). One should follow [Kubernetes storage classes][storage] general information to properly set up a storage class for any specific provider. Moreover, use the `WaitForFirstCustomer` option for [volumeBindingMode][binding]:
    ```yaml
    kind: StorageClass
    apiVersion: storage.k8s.io/v1
    metadata:
      name: topology-aware-standard
    volumeBindingMode: WaitForFirstConsumer
    < add provider-related parameters here >
  ...
  
    ```

- After creating this storage class, use it in the storage spec for the CRD:
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
[storage]: https://kubernetes.io/docs/concepts/storage/storage-classes/#parameters
[binding]: https://kubernetes.io/docs/concepts/storage/storage-classes/#volume-binding-mode