### Helm Instructions

The project includes in-tree helm templates to make installation simpler and repeatable. 
To install via helm follow the steps below:

 1) Make sure helm templates include all required files:
     ```bash
     $ make helm
     ```

 1) First check and change the values.yaml file to make sure it meets your requirements:
    e.g. in `helm/cassandra-operator/values.yaml`

 1) Install the operator
    ```
    $ helm install helm/cassandra-operator --namespace cassandra-operator 
    ```

 1) Create a Cassandra cluster (remember to check the `helm/cassandra/values.yaml` for correct values)
    ```
    $ helm install helm/cassandra -n test-cluster
    ```

The Helm templates are relatively independent and can also be used to generate the deployments yaml files:
```
$ helm template helm/cassandra-operator
```