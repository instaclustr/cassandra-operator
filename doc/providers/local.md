### To build and install the operator locally using minikube (or other setup) follow the below steps:

 1) In the project root directory e.g. cd ~/git/cassandra-operator/
    Start a minikube cluster with enough resources, cassandra is hungry!
    ```
    $ minikube start --cpus 4 --memory 4096 --kubernetes-version v1.14
    ```

 1) Use the minikube docker context 
    ```
    $ eval $(minikube docker-env)
    ```
    
    > Alternatively, use other VM/kvm based installs to create a local k8 environment.
    
 1) Proceed to [Op Guide](../op_guide.md) to install and launch the operator.

 1) If [multi-rack](../custom-configuration.md) is required, add labels to the local "nodes" to allow multi-rack pod placement. Have a look at [Custom configurations](../custom-configuration.md) for configuration options for the Cassandra clusters.
 
 1) Proceed to [Running a cluster](../run_cluster.md) to launch the Cassandra cluster.
