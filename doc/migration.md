## Migration

Migration between versions should work fine until API does not change (the spec does not 
change its stucture). If this happens, you have to uninstall CRDs and install them again.
Unfortunately, uninstalling your CRDs means that your resources which were started / deployed 
will be effectively deleted as well as a resource can not live  without its CRDs in operators case. 
You have to pay utmost attention to this. This is not the specific of this operator but that is how 
whole operator framework works.

If spec / API is not changed, you should be able to un-deploy operator itself and deploy new version.
Un-deploying the operator is harmless as long as you are not touching CRDs. Your pods will continue 
to run and after you redeploy the operator, the operator will continue to talk to them. While this is 
rather risky situation, if exercised and tried out beforehand, it should be a routine.

Notable changes between the versions will be summarised here:

### v6.0.2

`userSecretVolumeSource` is still an array as it was in v6.0.1, but it is not mounted under 
`/tmp/user-secret/` but under `/tmp/{name-of-that-secret}.`

The mere upgrade in this case will not work out of the box as this change is not backward compatible 
if one is using TLS on client-node or inter-node. One would have to firstly update the config maps 
and restart these pods so the change is propagated and right paths are set.

### v6.0.1

`userSecretVolumeSource` in spec became array. You can enumerate more than one 
secret. This is handy e.g. for Cassandra container when you have different secrets 
for different things, being it certificates and secrets for inter-node and client-node 
which are externally treated differently and they are separate secrets.

### v6.0.0

Operator SDK was updated to version 0.16.0. Operator's Go version was updated to 1.13.8.

### v5.1.0

Sidecar image is smaller (in MBs) because Sidecar from now on integrates 
backup-restore application as well. Previously, back-restore was standalone 
command (JAR) as it was used upon restores as well as for performing backup 
requests from operator so Sidecar depended on its API. This is now consolidated and Sidecar is able to be 
invoked as a server application as well as CLI application for taking backups / restores 
so there is not any need to have backup-restore application standalone which renders 
whole image smaller.

### v5.0.0

No notable changes. Cassandra image was updated to use Cassandra 3.11.6.

### v4.0.0 and above

The naming convention has changed in version 4.0.0 (hence 5.0.0 included). From now on, you should have your specs like this:

```
apiVersion: cassandraoperator.instaclustr.com/v1alpha1
kind: CassandraDataCenter
metadata:
  name: test-cluster-dc1
  labels:
    app: cassandra
datacenter: dc1 
cluster: test-cluster
spec:
 ... other fields
```

You might find more complete example in `examples` directory.

This gives you the following pod:

```
cassandra-test-cluster-dc1-west1-a-0
```

The pattern is:

```
cassandra-{cluster}-{datacenter}-{rack}-{pod-in-a-rack}
```

The reason we have changed this is that we are preparing for multi-dc deployments and with the previous case it was not 
easy / possible to do. Other thing is that backups were not differentiating cluster name as well so you could not, for example, 
backup a dc of a different cluster when the name of dc was same.

Unfortunately this is a breaking change and you are basically forced to recreate your cluster from scratch. 
We are sorry for the inconvenience and we are aware of the fact that making changes like these makes life more difficult 
for users. From this point on, we want to freeze whole API for time being so we guarantee that 
API will not change for foreseeable future.

The operator of version 4.0.0 is also built on top of Operator SDK framework of version [0.13.0](https://github.com/operator-framework/operator-sdk/blob/master/doc/migration/version-upgrade-guide.md#v013x).

## How to migrate from operator 2.x to 4.x and later

If you end up in a situation you have a running cluster deployed by operator of version lower than 2.6 and you are 
migrating to e.g. 5.x, one way how to migrate is as follows:

1) with old operator, backup your cluster to a bucket of your choosing
2) un-deploy old operator and all related CRDs, this will delete your old cluster
3) deploy new operator and all related CRDs
4) do *not* deploy completely new and empty cluster with intention to somehow load data there, you _might_ do that but 
it is unnecessarily complicated. If you backup a cluster, you can indeed restore it. One way backup without the 
possibility to restore does not make a lot of sense. The procedure how to restore a cluster from backup is [here](https://github.com/instaclustr/cassandra-operator/blob/master/doc/backup_restore.md#restore)

For the cluster deployed on version 2.x and a need to run it on 5.x, your backup spec has to comply with new format because 
in version 5.x, we have introduced datacenter name into backup destination URI so new backup spec has to follow that. 
The problem is, how to actually have that _new_ backup spec when you have backed up with old operator?

1) delete old backup resource (it should be already deleted if you exected step 2) above as undeployment of CRDs will 
automatically delete all related resources created from it.
2) Take your old backup spec you have used for backing up your old cluster and make sure it follows new format. There is 
example in examples directory in this repository where new backup spec it shown.
3) Follow [this answer](https://github.com/instaclustr/cassandra-operator/blob/master/doc/backup_restore.md#what-if-i-have-files-remotely-but-i-do-not-have-backup-spec-to-reference-to).
You can create that _new_ backup spec which will not actually backup anything, there is the field `justCreate` for that.
4) Apply new spec for cluster deployment which references the backup you have just created (even dummy one). The restoration 
from a cloud is transparent, it is as if you have deployed completely new cluster but if you reference that backup, it will 
fetch all files under the hood as part of the init container and it will continue to bootstrap as nothing happened.

In order to make this transition, you will have to move data in your bucket around to match the new format of backup destionation. 
You have to move files around to have it in this structure "/cluster-name/datacenter-name/pod-name/{data,manifests,tokens}". For example:

```
test-cluster/dc1/cassandra-test-cluster-dc1-rack1-0/{data,manifests,tokens}
                 cassandra-test-cluster-dc1-rack1-1/{data,manifests,tokens}
                 cassandra-test-cluster-dc1-rack1-2/{data,manifests,tokens}
                 cassandra-test-cluster-dc1-rack1-3/{data,manifests,tokens}
                 cassandra-test-cluster-dc1-rack1-4/{data,manifests,tokens}
```

Where

* `test-cluster` is same value as in field `cluster` in your spec
* `dc1` is same value as in field `datacenter` in your spec
* `cassandra-test-cluster-dc1-rack1-0` is name of new pod, it is of format `cassandra-{cluster}-{datacenter}-{rack}-n`

`rack` is "rack1" if not specified otherwise (consult Rack Awareness section in configuration doc). `n` is pod number.

For successful restoration, it will be also important to use same name of a cluster as it was set in old cluster. If not sure, you 
can have this information from this table:

```
cassandra@cqlsh> select cluster_name from system.local ;

 cluster_name
--------------
 test-cluster

(1 rows)
```