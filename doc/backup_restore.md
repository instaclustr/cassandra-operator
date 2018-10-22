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
      
  prometheusEnabled: false
```

To create a cluster using this yaml file use `kubectl apply -f myBackupCluster.yaml`

## Configuring GCP Object Storage via environment variables
First create a secret in kubernetes to hold a Google service account token/file (assuming they are stored in files named access and secret respectively).

`kubectl create secret generic gcp-auth-reference --from-file=my_service_key.json`

You can inspect the secret created via `kubectl describe secrets/reference`

Create a `CassandraDataCenter` CRD that injects the secret as a file. Configure the environment variables that matches the GCP client JAVA SDKs expected env variables for controlling bucket name, region and service account key file location:

```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraDataCenter
metadata:
  name: foo-cassandra
  labels:
    app: cassandra
    chart: cassandra-0.1.0
    release: foo
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
  userSecretSource:
    name: gcp-auth-reference
    items:
      - key: my_service_key.json
        path:  my_service_key.json
  env:
    - name: GOOGLE_APPLICATION_CREDENTIALS
      value: "/tmp/user-secret/my_service_key.json"
    - name: GOOGLE_CLOUD_PROJECT
      value: "cassandra-operator"
    - name: BUCKET_NAME
      value: "my-cassandra-operator"
      
  prometheusEnabled: false
```

## Taking a backup
The Cassandra-operator manages backups via a backup CRD object in Kubernetes, this makes it easy to track and audit backups, you can schedule backups via a cron mechanism that creates new CRDs etc.
It also allows you to reference a known backup when you restore. Backups will target all pods that match the labels specified on the backup CRD, you can backup multiple clusters via a single backup CRD. 
Node identitiy is maintained via the backup path.

The name field in metadata will be used as the snapshot name (e.g. via nodetool snapshot and when uploaded to the external storage provider).

To backup the cluster we just created, create the following yaml file (called `backup.yaml` in this example):

### AWS
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
  status: "PENDING"
```

### GCP
```yaml
apiVersion: stable.instaclustr.com/v1
kind: CassandraBackup
metadata:
  name: backup-hostname
  labels:
    cassandra-datacenter: foo-cassandra
spec:
  backupType: GCP_BLOB
  target: ben-cassandra-operator
  status: "PENDING"
```

In the spec field, `backupType` indicates what storage mechanism to use and `target` will be the undecorated location (e.g. the S3 bucket). To create the backup run `kubectl apply -f backup.yaml`

The Cassandra operator will detect the new backup CRD and take snapshots of all nodes that match the same labels as the `CassandraBackup`. You can follow the progress of the backup by following the sidecar logs for each node included in the backup.
The backup will include all user data as well as the system/schema tables. This means on restoration your schema will exist.

## Restoring from a backup
The Cassandra-operator allows you to create a new cluster from an existing backup. To do so, make sure you have already taken a backup from a previous/existing cluster. The add the property `restoreFromBackup: backup-name` to the CassandraDataCenter CRD `spec`. 
This is the name of the backup (CRD) you wish to restore from. Your full yaml file will look like:

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
  restoreFromBackup: backup-name
```

The new cluster will download the sstables from the existing backup before starting Cassandra and restore the tokens associated with the node the backup was taken on. The schema will be restored along side the data as well. 
The operator will match the new clusters nodes to backups from previous cluster based on the ordinal value of the pod assigned by its stateful set (e.g. old-cluster-0 will be restored to new-cluster-0). 
You should always restore to a cluster with the same number of nodes as the original backup cluster. Restore currently works against just a single DC, if you backup using a broader set of lables, restore from that broader label group won't work.
