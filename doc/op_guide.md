## Installing the Cassandra Operator

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
    
### Deploy a Cassandra Cluster

 1) To deploy a cluster on AKS, refer to [Running a cluster on AKS](providers/aks.md)
 1) To deploy a cluster on GKE, refer to [Running a cluster on GKE](providers/gke.md)
 1) To deploy a cluster in other environments, refer to [Setup a local environment](providers/local.md) for local setup and/or deploy the cluster with [generic instructions](run_cluster.md)


[aks]: https://azure.microsoft.com/en-in/services/kubernetes-service/
[gke]: https://console.cloud.google.com/kubernetes
[crds]: https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions
[psps]: https://kubernetes.io/docs/concepts/policy/pod-security-policy/
[rbac]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
