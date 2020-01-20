# Intro
The cassandra-operator supports taking backups of a cluster managed by the operator and restoring those backups into a new cluster. This document outlines how to configure and manage backups and restores.

## Configuring backups for your cluster
Depending on your environment and Kubernetes distribution, these steps may be different. 
The backup target location (where your backups will be stored) will determine how you configure your cluster. 
Each supported cloud-baed backup location (Google Cloud Storage, AWS S3 and Azure Blobstore) utilises the standard Java clients from those cloud providers
and those clients default credentials chains. 

This means you can generally pass in credentials via environment variables, default credential paths or let 
the library discover credentials via mechanisms such as instance roles. Either way you will need to ensure that the backup agent can access credentials for the target location.
This example will cover using environment variables provided by a [kubernetes secret](https://kubernetes.io/docs/concepts/configuration/secret/).

## Configuring environment variables via secrets
First create a secret in Kubernetes to hold an IAM users access and secret keys (assuming they are stored in files named access and secret respectively).

```
$ cat example-backup-secrets.yaml 
apiVersion: v1
kind: Secret
metadata:
  name: backup-secrets
type: Opaque
stringData:
  awssecretaccesskey: __enter__
  awsaccesskeyid: __enter__
  awsregion: __enter__
  azurestorageaccount: __enter__
  azurestoragekey: __enter__

```

Create these secrets by following command.

`kubectl create secret generic backup-secrets --from-file=./example-backup-secrets.yaml`

For talking to GCP, environment variables are not enough. There needs to be a file created as a secret 
which will be mounted to container transparently and picked up by GCP initialisation mechanism.

```
kubectl describe secrets gcp-auth-reference 
Name:         gcp-auth-reference
Namespace:    default
Labels:       <none>
Annotations:  <none>

Type:  Opaque

Data
====
gcp.json:  2338 bytes
```

In `gcp.json`, there is your service account you can get by following this [page](https://cloud.google.com/iam/docs/creating-managing-service-account-keys).

```
$ kubectl get secrets
NAME                             TYPE                                  DATA   AGE
backup-secrets                   Opaque                                5      13d
gcp-auth-reference               Opaque                                1      11d
... other secrets omitted
```

Create a `CassandraDataCenter` CRD that injects the secret as environment variables that matches the AWS client libraries expected env variables:

```yaml
  backupSecretVolumeSource:
    secretName: gcp-auth-reference
    type: array
    items:
      - key: gcp.json
        path: gcp.json
  sidecarEnv:
    - name: GOOGLE_APPLICATION_CREDENTIALS
      value: "/tmp/backup-creds/gcp.json"
    - name: AWS_ACCESS_KEY_ID
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awsaccesskeyid
    - name: AWS_SECRET_ACCESS_KEY
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awssecretaccesskey
    - name: AWS_REGION
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awsregion
    - name: AZURE_STORAGE_ACCOUNT
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: azurestorageaccount
    - name: AZURE_STORAGE_KEY
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: azurestoragekey

```

The resulting full CRD yaml will look like this, you will find always updated example 
in `examples` directory.
```yaml
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraDataCenter
metadata:
  name: test-dc-cassandra
  labels:
    app: cassandra
spec:
  nodes: 3
  cassandraImage: "gcr.io/cassandra-operator/cassandra:3.11.5"
  sidecarImage: "gcr.io/cassandra-operator/cassandra-sidecar:latest"
  imagePullPolicy: IfNotPresent
  racks:
    - name: "west1-b"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-b
    - name: "west1-c"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-b
    - name: "west1-a"
      labels:
        failure-domain.beta.kubernetes.io/zone: europe-west1-a
  imagePullSecrets:
    - name: regcred
  resources:
    limits:
      memory: 1Gi
    requests:
      memory: 1Gi

  dataVolumeClaimSpec:
    accessModes:
      - ReadWriteOnce
    resources:
      requests:
        storage: 500Mi
  prometheusSupport: false
  privilegedSupported: true

  backup:
    restore: true
    backupName: test-cassandra-backup-restore-s3
    backupSecretVolumeSource:
      secretName: gcp-auth-reference
      type: array
      items:
        - key: gcp.json
          path: gcp.json

  sidecarEnv:
    - name: GOOGLE_APPLICATION_CREDENTIALS
      value: "/tmp/backup-creds/gcp.json"
    - name: AWS_ACCESS_KEY_ID
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awsaccesskeyid
    - name: AWS_SECRET_ACCESS_KEY
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awssecretaccesskey
    - name: AWS_REGION
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: awsregion
    - name: AZURE_STORAGE_ACCOUNT
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: azurestorageaccount
    - name: AZURE_STORAGE_KEY
      valueFrom:
        secretKeyRef:
          name: backup-secrets
          key: azurestoragekey
```

To create a cluster using this yaml file use `kubectl apply -f example-datacenter.yaml`

## Taking a backup
The Cassandra operator manages backups via a backup CRD object in Kubernetes, this makes it easy to track and audit backups, you can schedule backups via a cron mechanism that creates new CRDs etc.
It also allows you to reference a known backup when you restore. Backups will target all pods that match the labels specified on the backup CRD, you can backup multiple clusters via a single backup CRD. 
Node identity is maintained via the backup path.

The name field in metadata will be used as the snapshot name (e.g. via `nodetool snapshot` and when uploaded to the external storage provider).

To backup the cluster we just created, create the following yaml file (called `backup.yaml` in this example):

```yaml
$ cat example-backups.yaml 
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraBackup
metadata:
  name: test-cassandra-backup
  labels:
    app: cassandra
spec:
  cdc: test-dc-cassandra
  storageLocation: "s3://cassandra-testdc-bucket"
  snapshotTag: "mySnapshotTag"
```

You need to specify `cdc` of a cluster to backup, this should reflect `metadata.name` in CRD of created cluster. 
Secondly, you need to specify `storageLocation` which tells what cloud you want to backup a cluster to. For instance, 
in the above example, a backup will be done in Amazon S3 under bucket `cassandra-testdc-bucket` and snapshot will 
be named `mySnapshotTag.` All these fields are required. For backing up to Azure, use `azure://` and for GCP use `gcp://`.

Blob containers / storage buckets needs to be 
created before backups are taken and they are not created automatically. `snapshotLocation` path is automatically 
appended with name of a cluster and finally with name of a node (its hostname) internally.

To create the backup run `kubectl apply -f backup.yaml`

The Cassandra operator will detect the new backup CRD and take snapshots of all nodes that match the same labels as the `CassandraBackup`. You can follow the progress of the backup by following the sidecar logs for each node included in the backup.
The backup will include all user data as well as the system/schema tables. This means on restoration your schema will exist.

You can inspect the progress of backup like:

```
$ kubectl get cassandrabackups.cassandraoperator.instaclustr.com 
NAME                    STATUS    PROGRESS
test-cassandra-backup   RUNNING   83%
```

```
$ kubectl describe cassandrabackups.cassandraoperator.instaclustr.com 
Name:             test-cassandra-backup
... other fields omitted
Status:
  Node:      cassandra-test-dc-cassandra-west1-a-0
  Progress:  66%
  State:     RUNNING
  Node:      cassandra-test-dc-cassandra-west1-b-0
  Progress:  48%
  State:     RUNNING
```

Eventually, progress will reach `COMPLETED` state. 

## Restoring from a backup

The Cassandra operator allows you to create a new cluster from an existing backup. 
To do so, make sure you have already taken a backup from a previous/existing cluster.

To achieve the restoration, you have to set `spec.backup.restore` on CDC to true and apply it. `CassandraBackup` name is, 
surprisingly, referenced by field `spec.backup.backupName`. `CassandraBackup` name has to already exist otherwise 
operator will not know the bucket to download files from etc. You can list your backups by `kubectl describe cassandrabackups`.

In some cases, you do have remote backup but you have not backed it up by the cluster you are working on. In this case, 
you may _reconstruct_ a `CassandraBackup` by simply applying it, pretending you are going to take a backup, but 
the run of that backup will be dummy and it will result only into creation of `CassandraBackup` but no action would be taken.
You have to setup `justCreate: true` as top-level field (not in `spec`). 
 
Then add the property `restoreFromBackup: test-cassandra-backup` to the CassandraDataCenter CRD `spec`. 
This is the name of the backup (CRD) you wish to restore from. All other fields on your original CRD will be same, you just add that 
one field into spec and apply it.

It is possible to either create a cluster with completely same name. If you want to restore into a cluster which datacenter 
would differ, you can do it too without any problems. If you reference name of a `CassandraBackup` in CDC in `spec.backup.backupName`,
even that CDC is named differently, it will internally resolve. Hence you are practically making a clone of a cluster.

Restore process will also take care of initial tokens and other configuration bits automatically. 
The new cluster will download the sstables from the existing backup before starting Cassandra and 
restore the tokens associated with the node the backup was taken on.

The schema will be restored along side the data as well. You should always restore to a cluster with the same 
number of nodes as the original backup cluster. Restore currently works against just a single DC, i
if you backup using a broader set of labels, restore from that broader label group won't work.
