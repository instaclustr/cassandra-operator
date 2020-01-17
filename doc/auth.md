# Auth

Cassandra operator is by default set up with the following 
configuration properties. `authenticator` and `role_manager` 
are custom and they are implemented in Kubernetes addons in this 
repository.

```
authenticator: KubernetesAuthenticator
authorizer: CassandraAuthorizer
role_manager: KubernetesRoleManager
```

There are four roles.

## `cassandra` role

The `cassandra` role is good old default role you know from 
out-of-the-box Cassandra installation. The default password 
is `cassandra` and it has to be specified on your logging attempts. 
Default password might be changed etc.

By default, you can not log in with this role. If you really want to log in, 
there has to be the file called `/etc/cassandra/.allow-default-cassandra-user` is `cassandra` pod. 
This file can be empty, but it has to be readable by `cassandra` Linux user under which Cassandra 
process is running. If this file does exist (which by default does not), you can log in with 
this role. Hence, if you start your Cassandra cluster for the first time, by default, you will not 
be able to log in with this role.

Even it is possible to log in only in case respective marker file exists, it is 
a good practice to change default password for `cassandra` role to something random 
and store offline. In case there is any need for this role ever again, one has to 
create a file and use such password. Even if the marker file would be left there 
after human operator is done with it, there would still be the need for a (random) password.

## `admin` role

`admin` role is _superuser_ role (as `cassandra` is) and by default it has password `admin`. This 
role is active out-of-the box. You should do all administration operations and tasks (including the 
creation of non-superuser roles) for respective applications by this role.

## `probe` role

This role is a _technical_ role. We are checking if a Cassandra node is truly up from Kubernetes 
perspective by invoking `cqlsh` select command on some system keyspace. If this query returns a meaningful 
result, we are sure that particular node is indeed up and responsive.

If we used `cassandra` role for this task, the password might change over time by a user of that cluster 
as such so we would not know what password to use upon such queries.

You might ask, _so how is this different for `probe` role?_ The trick here is that we are not using any 
password at all. Actually, we do, but that password is randomly generated upon every such query and this 
password is stored into a file. Once we log in with such randomly generated password, `KubernetesAuthenticator` 
will see that `probe` is trying to log in, so it reads the very same file from the filesystem where 
we inserted that password and it will compare these password, which will indeed match. But it is important 
to realise that such passwords will be same but at the same time different every time. After the query is finished, the file 
with such random password is deleted.

The _Readiness probe_ is a Kubernetes feature and it is executed periodically every _n_ seconds. 
`probe` role is created on the very first readiness probe execution and it is hardcoded to be _non-superuser_ role 
which can not do anything else but a `select` on `system` keyspaces. It has all permissions revoked so it can not 
do anything (even selects) on any other resources.

## `sidecar` role

`sidecar` role is serving the very same purpose as `probe` but it might be used in 
the future for CQL queries issued from the sidecar container.