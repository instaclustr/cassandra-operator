# Intro

The cassandra-operator supports taking backups of a cluster managed by the operator and restoring those backups into a new cluster. This document outlines how to configure and manage backups and restores.

To backup a cluster means that the whole state of a cluster, per node, is uploaded to remote location. _The whole state_ 
means that by default, it will upload all SSTables to some cloud destination. Currently we are supporting upload to S3, 
Azure or GCP.

The backup procedure is initiated by Cassandra operator itself once you apply a backup spec to Kubernetes. The backup 
controller watches this CRD and it will call Sidecar of each node via HTTP where it submits a backup operation. Sidecar 
internally uses our other project - Cassandra Backup / Restore - via which it will take a snapshot of a node and 
all created SSTables are uploaded. This is happening in parallel on each node and SSTables themselves are also 
uploaded in parallel. SSTables are stored in a bucket. Currently, a bucket has to be created before-hand.

To restore a cluster means that before a node is started, SSTables are downloaded from remote location where they 
were previously uploaded. They are downloaded into the location where Cassandra picks them up upon its start to 
it seems as if these files where there all the time. A restoration is node node by node, as each node starts, so it is 
restored.

Upon restoration, we are setting _auto_bootstrap_ to _false_ and we set _initial_token_ to be equal to tokens 
a node was running with when it was about to be backed up.

## Backup

The very first thing you need to do in order to make a backup happen is to specify credentials for a cloud you want 
your SSTables to be backed up. The authentication mechanism varies across clouds. If you were to support all targets 
we currently provide, you would have to creata a Kubernetes _Secret_ which would look like this:

````
apiVersion: v1
kind: Secret
metadata:
  name: cloud-backup-secrets
type: Opaque
stringData:
  awssecretaccesskey: _enter_here_aws_secret_access_key_
  awsaccesskeyid: _enter_here_aws_access_key_id_
  awsregion: _enter_here_aws_region_eu-central-1_
  awsendpoint: _enter_aws_endpoint_if_any_
  azurestorageaccount: _enter_here_azure_storage_account_
  azurestoragekey: _enter_here_azure_storage_key_
  gcp: 'put here content of gcp.json from Google GCP backend'
```` 

In order to backup to your cloud of choice, you **have to** use same keys in `stringData` as above. Cassandra operator 
reacts to these keys specifically and to nothing else. You do not need to specify _every_ key. For example, if you 
plan to upload only to Azure, just create a secret which contains only Azure specific keys.

After the secret is specified, you can proceed to backup, you have to _apply_ this spec:

````
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraBackup
metadata:
  name: test-cassandra-backup-restore-s3
  labels:
    app: cassandra
spec:
  cdc: test-dc-cassandra
  storageLocation: "azure://stefan-cassandra-testdc-bucket"
  snapshotTag: "restore-test"
secret: cloud-backup-secrets
````

First of all, notice how we are calling our backup - _test-cassandra-backup-restore-s3_, we will use this 
name once we want to restore a cluster. Secondly, `cdc`, that is name of our cluster. Notice also _storageLocation_, 
its prefix is _azure_ so it means that we are going to perform a backup to Azure. Next, bucket name is 
_stefan-cassandra-testdc-bucket_ so this will be the bucket a backup operation will upload all files to. 
Currently, this bucket has to exist beforehand. _snapshotTag_ follows - this is the name of a snapshot a 
backup procedure does. As you can imagine, if you apply this spec multiple times with different snapshots over time, 
you will end up with different data which reflects different state of your cluster.

Lastly, you have to specify _secret_. Here, we reference the name of the secret created at the beginning.

If you apply this CRD, you can track the progress by _getting_ or _describing_ respective resource:

```
$ kubectl get cassandrabackups.cassandraoperator.instaclustr.com 
NAME                               STATUS    PROGRESS
test-cassandra-backup-restore-s3   RUNNING   83%
```

```
$ kubectl describe cassandrabackups.cassandraoperator.instaclustr.com 
Name:             test-cassandra-backup-restore-s3
... other fields omitted
Status:
  Node:      cassandra-test-dc-cassandra-west1-a-0
  Progress:  66%
  State:     RUNNING
  Node:      cassandra-test-dc-cassandra-west1-b-0
  Progress:  48%
  State:     RUNNING
  Node:      cassandra-test-dc-cassandra-west1-c-0
  Progress:  45%
  State:     RUNNING
``` 

Once all pods are backed up, `Progress` will be 100% and `State` will become `Completed`.

Congratulations, you have backed up your cluster. Let's see how you actually restore it.

## Restore

The restoration is very simple. Firstly, be sure your secret exists as specified so we can talk to a cloud storage 
upon restore. Restoration is done by init container, before Cassandra and Sidecar is even started. Init container 
will download all data from a cloud so when Cassandra starts, it feels as if it just started.

All you need to do is to specify this snippet in CDC:

```
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraDataCenter
metadata:
  name: test-dc-cassandra
  labels:
    app: cassandra
spec:
  # a bunch of other configration parameters
  restore:
    backupName: test-cassandra-backup-restore-s3
    secret: cloud-backup-secrets
```

All CDC spec is as you are used to, it differs only on `restore`. `backupName` is, surprisingly, name of a backup. 
That backup object has to exist. `secret` name is name of a secret from beginning. We inject name of this 
secret to init container so restoration procedure will resolve all necessary credentials from Kubernetes dynamically.

### Can I change credentials in my secret?

Absolutely. This is the reason why we have implemented it in that way. The trick is that once a backup operation 
request is sent to a Sidecar container, it will internally reach to Kubernetes API, from within, by official Kubernetes 
Java API client and it will try to resolve credentials for a cloud you have specified a prefix in _storageLocation_ for.
Hence you can change your credentials as you wish because they will be retrieved from Kubernetes every time dynamically.

### What if I have files remotely but I do not have backup spec to reference to?

No worries. Imagine you have a completely different Kubernetes cluster you want to restore a Cassandra cluster into. 
Similarly, maybe you have just lost your backup spec accidentally. In either case, we can create a backup spec but when 
we create it, it will not proceed to actual backup because there is _nothing to backup_. You have to specify 
a field with name `justCreate` and set it to true like this: 

```
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraBackup
metadata:
  name: test-cassandra-backup-restore-s3
  labels:
    app: cassandra
spec:
  cdc: test-dc-cassandra
  storageLocation: "azure://stefan-cassandra-testdc-bucket"
  snapshotTag: "restore-test"
secret: cloud-backup-secrets
justCreate: true         <------ see?
```

### Can I create a backup of same CDC into same storage location with same snapshot name?

No. In spite of snapshots being deleted automatically after files where uploaded, it does not make sense to upload something 
_twice_ under same snapshot. Why would you even want that? Rule of thumb is to include some date and time information 
into its name so you can return back to it in the future, referencing arbitrary snapshot.

### My AWS node instance has credentials to S3 itself

That is fine. So do not specify any credentials related to AWS. Firstly it will try to look them up and if it fails, it will 
eventually fallback to last chance to authenticate. This is delegated to S3 client builder itself.

If _awsendpoint_ is set but _awsregion_ is not, the backup request fails. If _awsendpoint_ is not set but _awsregion_ is set, 
only this region will be set.

If you do not specify _awssecretaccesskey_ nor _awsaccesskeyid_, as stated above, it will fallback to instance authentication mechanism.

### With what configuration is a Cassandra node started after being restored?

We are setting _auto_bootstrap: false_ and _initial_token_ with tokes a respective node was running with when it was backed up.