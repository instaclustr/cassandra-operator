##Resources and Labels
### TODO complete overview of the resources managed by the operator...
Currently the operator creates the following k8s resources:
- A stateful set that manages Cassandra nodes
- A seed discovery headless service (two nodes per DC are included via label selections). This is used as the Cassandra seed list.
- A cluster headless service (good for client discovery)
