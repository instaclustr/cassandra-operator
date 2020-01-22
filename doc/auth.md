# Auth

Cassandra operator is by default set up with the following 
configuration properties in CDC spec.

```
cassandraAuth:
  authenticator: AllowAllAuthenticator
  authorizer: AllowAllAuthorizer
  roleManager: CassandraRoleManager
```

By default, Cassandra will not ask you for any password and it will behave as if the authentication 
is completely turned off.

You can change it to classic password-based auth like following:

```
  cassandraAuth:
    authenticator: PasswordAuthenticator
    authorizer: CassandraAuthorizer
    roleManager: CassandraRoleManager
```

**As of now, you can not change your auth method after you create and scale cluster.**

When you are starting your cluster from scratch, e.g. from 1 to 3 nodes, Cassandra 
sets up `system_auth` keyspace which has the replication strategy of `SimpleStrategy` and replication factor of 1.

This might work all fine but since nodes are added to a cluster and you have `SimpleStrategy`, data 
are stored just on one node. If you scale that cluster down and a node which happened to go down is the 
node with your auth data, you might not be able to log in.

Therefore it is necessary to change you replication factor to number of nodes in a cluster and replication 
strategy should be changed to `NetworkTopologyStrategy` (especially of your cluster has some 
rack-awareness). After that, on each node, you should do:

For example, if your cluster happens to have 3 nodes, alter your `system_auth` keyspace after 
all nodes are up like this by logging in to any node and executing:

```
cqlsh>  ALTER KEYSPACE system_auth WITH replication = {'class': 'NetworkTopologyStrategy', 'test-dc-cassandra': 3};
```

`test-dc-cassandra` is name of your datacenter.

The above command only specified that from now on, your `system_auth` keyspace has different network topology 
strtegy and each piece of data will be stored three times (on three different nodes). It does not mean that 
by altering of your keyspace, these data are really there. There is not magic behind that. You need to do it 
manually like this: 

```
$ nodetool -h ip.of.a.pod repair -full
```

where `ip.of.a.pod` is an IP / hostname of a respective pod in a cluster. Now if you scale your cluster down from 
e.g. 4 nodes to just 2, you will be still able to log in without any problems.

**You have to execute this nodetool command on every node.** 

You should repeat this technique as you see it fit. If you have e.g. 5 nodes, your system_auth 
should be replicated everywhere.

## `cassandra` role

The `cassandra` role is good old default role you know from 
out-of-the-box Cassandra installation. The default password 
is `cassandra` and it has to be specified on your logging attempts. 
Default password should be changed etc.

## `probe` role

This role is a _technical_ role. We are checking if a Cassandra node is truly up from Kubernetes 
perspective by invoking `cqlsh` select command on some system keyspace. If this query returns a meaningful 
result, we are sure that particular node is indeed up and responsive.

If we used `cassandra` role for this task, the password might change over time by a user of that cluster 
as such so we would not know what password to use upon such queries.

The _Readiness probe_ is a Kubernetes feature and it is executed periodically every _n_ seconds. 
`probe` role is created on the very first readiness probe execution and it is hardcoded to be _non-superuser_ role.