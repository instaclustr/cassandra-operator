##Developers Guide
The Cassandra operator is primarily written in Java and uses Maven as the build and dependency management system.

###Operator
The main logic for the controller portion of the Cassandra operator can in found in the operator/ module. The Cassandra
sidecar also sits within this module as ends up being built in the same jar (with just a different main entry point), 
this reduces the number of docker image layers that get pulled. 

###Model
Defined kubernetes CRD objects can be found in the model/ module. These are defined by a json schema and there Java
classes are generated when the model/ module is built by maven. If you are having trouble with resolving model classes
in your IDE you made need to configure the target directory as a source/generated sources directory

###Backup
The somewhat stand-alone backup agent for Cassandra. It can be used as a separate stand alone backup process (controlled via
command line parameters) or embedded in the operator sidecar. 

###k8s-addons
This module contains add-on components that extend Cassandra and make it easier to work and operate in a Kubernetes environment.
The k8s-addon jar gets included Cassandra docker image and is dropped into the Cassandra classpath. 

##Building
To build the project and generate relevant jars, from the project root, run: 
```bash
mvn clean package
```
This will build all the submodules defined in the parent pom.xml

To build the relevant docker images, run:
```bash
./buildenv/build-all
```

This will by default build an images for the projects gcr.io development image repo, the component and the suffix `-dev` will be appended.
The default tag is the git short hash.
e.g. `gcr.io/cassandra-operator/cassandra-operator-dev:0cf96c3` 

This behavior can be overrode by defining `NO_DEV`, `REPO` and `TAG` environment variables. 