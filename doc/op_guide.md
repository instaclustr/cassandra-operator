## Installing the Cassandra operator

### Deploy the Operator

 1) Deploy the [CRDs][crds] used by the operator to manage Cassandra clusters:

    ```
    kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandrabackup_crd.yaml
    kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandracluster_crd.yaml
    kubectl apply -f deploy/crds/cassandraoperator_v1alpha1_cassandradatacenter_crd.yaml
    ```

 1) Deploy the [RBAC][rbac] resources and [pod security policies][psps] needed for the operator to run:
    ```
    kubectl apply -f deploy/operator_bundle.yaml
    ```
 1) Deploy the [RBAC][rbac] resources and [pod security policies][psps] used by the operator to create
Cassandra pods:

    >TODO: Remove this? This isn't mandatory on AKS as things work fine with the `default` SA.

    ```
    kubectl apply -f deploy/cassandra
    ```

 1) Deploy the operator itself:

    ```
    kubectl apply -f deploy/operator.yaml
    ```

 1) Verify the operator is running:

    ```
    kubectl get pods | grep cassandra-operator
    ```

    ```
    cassandra-operator-5755f6855f-t9hvm   1/1     Running   0          65s
    ```
    
### Deploy a Cassandra cluster

 1) To deploy a cluster on AKS, refer to [Running a cluster on AKS](providers/aks.md)
 1) To deploy a cluster on GKE, refer to [Running a cluster on GKE](providers/gke.md)
 1) To deploy a cluster in other environments, refer to [Setup a local environment](providers/local.md) for local setup and/or deploy the cluster with [generic instructions](run_cluster.md)


[aks]: https://azure.microsoft.com/en-in/services/kubernetes-service/
[gke]: https://console.cloud.google.com/kubernetes
[crds]: https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#customresourcedefinitions
[psps]: https://kubernetes.io/docs/concepts/policy/pod-security-policy/
[rbac]: https://kubernetes.io/docs/reference/access-authn-authz/rbac/
