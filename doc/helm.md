### Helm Instructions

We are using Helm 3.

There are Helm charts pushed to gcr.io registry as well. We are using experimental support of [Helm registries](https://helm.sh/docs/topics/registries/).

Charts are available in gcr.io under refs:

```
gcr.io/cassandra-operator/cassandra-chart:<version>
gcr.io/cassandra-operator/cassandra-operator-chart:<version>
```

The project includes in-tree helm templates to make installation simpler and repeatable.
 
To install manually:

 1) Make sure helm templates include all required files:
     ```bash
     $ make helm
     ```

 1) First check and change the values.yaml file to make sure it meets your requirements:
    e.g. in `helm/cassandra-operator/values.yaml` and `helm/cassandra/values.yaml`

 1) Install the operator
    ```
    $ helm install cassandra-operator helm/cassandra-operator --wait 
    ```

 1) Create a Cassandra cluster (remember to check the `helm/cassandra/values.yaml` for correct values)
    ```
    $ helm install cassandra helm/cassandra
    ```

The Helm templates are relatively independent and can also be used to generate the deployments yaml files, e.g:
```
$ helm template helm/cassandra-operator
```
