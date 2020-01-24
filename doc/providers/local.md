### To build and install the operator locally using minikube (or other setup) follow the below steps:

 1) In the project root directory e.g. cd ~/git/cassandra-operator/
    Start a minikube cluster with enough resources, Cassandra is hungry!
    ```
    $ minikube start --cpus 4 --memory 4096 --kubernetes-version v1.14
    ```

 1) Use the minikube docker context 
    ```
    $ eval $(minikube docker-env)
    ```
    
    > Alternatively, use other VM/kvm based installs to create a local k8 environment.
    
 1) If [multi-rack](../custom-configuration.md) is wanted, add labels to the local "nodes" to allow multi-rack pod placement. Have a look at [Custom configurations](../custom-configuration.md) for configuration options for the Cassandra clusters.
 
 1) Add Persistent Volumes to your environment. One way to do so would be using NFS-mounted volumes for the k8 environment via sharing local folders. For example, by creating and exposing `/volumes/00` to the kubernetes network, one could use the following yaml fragment to create the PV:
    ```yaml
    apiVersion: v1
    kind: PersistentVolume
    metadata:
      name: nfs00
    spec:
      capacity:
        storage: 1Gi
      accessModes:
        - ReadWriteOnce
        - ReadWriteMany
      persistentVolumeReclaimPolicy: Recycle
      nfs:
        server: <localhost IP in k8 network>
        path: "/volumes/00"
    ```
    You can repeat this action as many times as required for many folders, creating multiple persistent volumes.