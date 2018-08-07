# Intro
The cassandra-operator supports taking backups a cluster managed by the operator and restoring those backups into a new cluster. This document outlines how to configure and manage backups.

## Configuring backups for your cluster
Depending on your environment and kubernetes distribution, these steps may be different. 
The backup agent used by the operator leverages the Instaclustr [backup util](https://github.com/instaclustr/cassandra-backup). 
The backup target location (where your backups will be stored) will determine how you configure your cluster. 
Each supported cloud based, backup location (Google Cloud Storage, AWS S3 and Azure Blobstore) utilises the standard Java clients from those cloud providers
and those clients default credentials chains. 

This means you can generally pass in credentials via environment variables, default credential paths or let 
the library discover credentials via mechanisms such as instance roles. Either way you will need to ensure that the backup agent can access credentials for the target location.
This example will cover using environment variables provided by a [kubernetes secret](https://kubernetes.io/docs/concepts/configuration/secret/).

## Configuring AWS S3 via environment variables
First create a secret in kubernetes to hold an IAM users access and secret keys (assuming they are stored in files named access and secret respectively).

`kubectl create secret generic awsbackuptest --from-file=./access --from-file=./secret`

You can inspect the secret created via `kubectl describe secrets/awsbackuptest`

Create a `CassandraDataCenter` CRD that injects the secret as environment variables that matches the AWS client libraries expected env variables:

```yaml
  env:
    - name: AWS_ACCESS_KEY_ID
      valueFrom:
        secretKeyRef:
          key: access
          name: awsbackuptest
    - name: AWS_SECRET_ACCESS_KEY
      valueFrom:
        secretKeyRef:
          key: secret
          name: awsbackuptest
    - name: AWS_REGION
      value: us-west-2
```


The resulting full CRD yaml will look like this:
```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: foo-cassandra
  labels:
    app: cassandra
    chart: cassandra-0.1.0
    release: foo
    heritage: Tiller
spec:
  replicas: 3
  cassandraImage: "gcr.io/cassandra-operator/cassandra:latest"
  sidecarImage: "gcr.io/cassandra-operator/cassandra-sidecar:latest"
  imagePullPolicy: IfNotPresent
  resources:
    limits:
      memory: 512Mi
    requests:
      memory: 512Mi
  dataVolumeClaim:
    accessModes:
    - ReadWriteOnce
    resources:
      requests:
        storage: 100Mi
  env:
    - name: AWS_ACCESS_KEY_ID
      valueFrom:
        secretKeyRef:
          key: access
          name: awsbackuptest
    - name: AWS_SECRET_ACCESS_KEY
      valueFrom:
        secretKeyRef:
          key: secret
          name: awsbackuptest
    - name: AWS_REGION
      value: us-west-2
```

To create a cluster using this yaml file use `kubectl apply -f myBackupCluster.yaml`

## Taking a backup
The Cassandra-operator manages backups via a backup CRD object in Kubernetes, this makes it easy to track and audit backups, you can schedule backups via a cron mechanism that creates new CRDs etc.
It also allows you to reference a known backup when you restore. Backups will target all pods that match the labels specified on the backup CRD, you can backup multiple clusters via a single backup CRD. 
Node identitiy is maintained via the backup path.

To backup the cluster we just created, create the following yaml file (called `backup.yaml` in this example):

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraBackup
metadata:
  name: backup-hostname
  labels:
    cassandra-datacenter: foo-cassandra
spec:
  backupType: AWS_S3
  target: kube-backup-test-cassandra
```

In the spec field, backupType indicates what storage mechanism to use and target will be the location (e.g. the S3 bucket)