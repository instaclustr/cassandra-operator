## Installing the Cassandra Operator

## Requirements
 - A running kubernetes cluster setup with [Persistent Volumes][storage]. We advise running 1 Cassandra pod per k8 node, and hence please plan your environment accordingly.
  > Note: See [AKS](./providers/aks.md) notes to properly define parameters for clusters running on AKS.
 
  > Note: See [GKE PV](./providers/gke.md) for notes to setup the persistent volumes properly on GKE. 
  
  > Note: See [Local setup](./providers/local.md) for notes on setting up a local kubernetes cluster

### Deploy the Operator

 1) Deploy the [CRDs][crds] used by the operator to manage Cassandra clusters:

    ```
    kubectl apply -f deploy/crds.yaml
    ```

 1) Deploy the operator:

    ```
    kubectl apply -f deploy/bundle.yaml
    ```

 1) Verify the operator is running:

    ```
    kubectl get pods | grep cassandra-operator
    ```

    ```
    cassandra-operator-5755f6855f-t9hvm   1/1     Running   0          65s
    ```
### Custom Cassandra configurations

It is possible to configure Cassandra by providing custom configuration. Refer to [Custom configurations](custom-configuration.md) for options to configure your cluster.
    
## Deploy a Cassandra cluster

> NOTE: To deploy the Cassandra cluster, one can use an example yaml provided in the `examples` directory. There are 2 examples included in the repo:
>  - example-datacenter.yaml -> a full example will all the fields showing usage. Use it as a template for your usecase.
>  - example-datacenter-minimal.yaml -> the minimal example of the yaml. To use this example, you must also create a configMap called "cassandra-operator-default-config" that will have default values used by operator set:
>     ```yaml
>     apiVersion: v1
>     kind: ConfigMap
>     metadata:
>       name: cassandra-operator-default-config
>     data:
>       nodes: "3"
>       cassandraImage: gcr.io/cassandra-operator/cassandra:3.11.4
>       sidecarImage: gcr.io/cassandra-operator/cassandra-sidecar:latest
>       memory: 1Gi
>       disk: 1Gi 
>    ```
> This configMap is already loaded into your k8 environment if you've used `deploy/bundle.yaml` to load operator's configuration.

 1) Make sure to set all apropriate values and fields in the yaml `examples/example-datacenter.yaml`
 1) Deploy the cluster
     ```bash
     # kubectl apply -f examples/example-datacenter.yaml
     ```
 1) Wait for the pods to become ready:
    >NOTE: It could take a few minutes for the pods to converge while persistent volumes are being
    automatically provisioned and attached to the cluster nodes.

    ```
    kubectl get pods | grep cassandra-test
    ```

    ```
    NAME                                          READY   STATUS             RESTARTS   AGE
    cassandra-test-dc-cassandra-west1-a-0   2/2     Running            2          84m
    cassandra-test-dc-cassandra-west1-b-0   2/2     Running            0          83m
    cassandra-test-dc-cassandra-wesr1-c-0   2/2     Running            0          81m
    ```
 1) Verify the Cassandra cluster is healthy:

    ```
    kubectl exec cassandra-test-dc-cassandra-west1-a-0 -c cassandra -- nodetool status
    ```

    ```
    Datacenter: test-dc-cassandra
    ==========================
    Status=Up/Down
    |/ State=Normal/Leaving/Joining/Moving
    --  Address      Load       Tokens       Owns (effective)  Host ID                               Rack
    UN  10.244.1.14  87.41 KiB  256          62.9%             dcf940c2-18d2-4a3a-8abf-833acadeca7e  west1-a
    UN  10.244.2.9   87.38 KiB  256          69.6%             fd59fa32-aab0-485e-b04b-7ad4b75e54dd  west1-b
    UN  10.244.0.10  69.91 KiB  256          67.5%             9e4883a1-e822-472f-920f-f2fc36c340c8  west1-c
    ```

 1) Issue a sample query to the cluster:

    ```
    kubectl exec cassandra-test-dc-cassandra-west1-a-0 -c cassandra -- cqlsh -e "SELECT now() FROM system.local;" cassandra-test-dc-cassandra-nodes 
    ```

    ```
     system.now()
    --------------------------------------
     243e2fd0-d64a-11e9-b8a4-2dd801fa1b1c

    (1 rows)
    ```
### Resize a Cassandra cluster 

 1) Create a Cassandra cluster, if you haven't already:

    ```bash
    $ kubectl apply -f example/example-datacenter.yaml
    ```

 1) In `example/example-datacenter.yaml` the initial cluster size is 3. Modify the file and change `replicas` from 3 to 5.

    ```yaml
    spec:
      replicas: 5
      image: "gcr.io/cassandra-operator/cassandra:latest"
    ```

 1) Apply the size change to the cluster CR:
    ```bash
    $ kubectl apply -f example/example-datacenter.yaml
    ```
    The Cassandra cluster will scale to 5 members (5 pods):
    ```bash
    $ kubectl get pods
    NAME                                    READY     STATUS    RESTARTS   AGE
    cassandra-test-dc-cassandra-west1-a-0   2/2       Running   1          10m12s
    cassandra-test-dc-cassandra-west1-a-1   2/2       Running   2          3m2s
    cassandra-test-dc-cassandra-west1-b-0   2/2       Running   1          8m38s
    cassandra-test-dc-cassandra-west1-b-1   2/2       Running   1          1m4s
    cassandra-test-dc-cassandra-west1-c-0   2/2       Running   0          5m22s
    ```

 1) Similarly we can decrease the size of cluster from 5 back to 3 by changing the size field again and reapplying the change.

    ```yaml
    spec:
      replicas: 3
      image: "gcr.io/cassandra-operator/cassandra:latest"
    ```
    Then apply the changes
    ```bash
    $ kubectl apply -f example/example-datacenter.yaml
    ```
    
    > **Note: scaling up/down is a long operation that performs actions in the background that are not visible to kubernetes tools. It might take long time until some "activity" is seen (like nodes in `LEAVING` state or pods terminating). Please do not rerun this command many times, but just follow the sidecar log (via `kubectl logs <pod name> --container sidecar` and cassandra status via `kubectl exec <pod name> -c cassandra -- bash "nodetool status"`)**
    
## Cleanup

>**WARNING! The following will delete the Cassandra cluster deployed in the previous steps as well
>as all of its data.**

### Delete the Cassandra Cluster

 1) Delete the Cassandra cluster:

    ```
    kubectl delete -f examples/example-datacenter.yaml
    ```

 1) Delete the PVCs created automatically for the pods:

    ```
    kubectl delete pvc data-volume-cassandra-cassandra-test-{rack name}-{num}
    ```

### Delete the Operator

 1) Delete the operator:

    ```
    kubectl delete -f deploy
    ```

 1) Delete the RBAC and PSP resources:

    ```
    kubectl delete -f deploy/cassandra
    ```

 1) Delete the CRDs:

    ```
    kubectl delete -f deploy/crds.yaml 
    ```


[aks]: https://azure.microsoft.com/en-in/services/kubernetes-service/
[gke]: https://console.cloud.google.com/kubernetes
[crds]: https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions
[psps]: https://kubernetes.io/docs/concepts/policy/pod-security-policy/
[rbac]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
[storage]: https://kubernetes.io/docs/concepts/storage/persistent-volumes/
