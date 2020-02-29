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