## Developers Guide
### Operator
The main logic for the controller portion of the Cassandra operator can be found in cmd/ and pkg/ directories and is primarily 
written in Go. The sidecar can be found in java/ and is written in Java.

### Model
Defined kubernetes CRD objects can be found in the model/ module. These are defined by a json schema and there Java
classes are generated when the model/ module is built by maven. If you are having trouble with resolving model classes
in your IDE you made need to configure the target directory as a source/generated sources directory

### Backup
The somewhat stand-alone backup agent for Cassandra. It can be used as a separate stand alone backup process (controlled via
command line parameters) or embedded in the operator sidecar. 

### k8s-addons
This module contains add-on components that extend Cassandra and make it easier to work and operate in a Kubernetes environment.
The k8s-addon jar gets included Cassandra docker image and is dropped into the Cassandra classpath. 

## Building
You will need to ensure your build environment has the following dependencies installed:
* jdk 8
* maven
* docker 

To build the project, compile the go project, generate images and generate relevant jars, from the project root, run: 
```bash
make
```

This will by default build an images for your local image repo. If you wish to run the operator images (e.g. from the example yaml files or the helm package). You will need to make sure the repo, image and tag all match the output of what you see when running `docker images`

This behavior can be overridden by defining `NO_DEV`, `REGISTRY` and `TAG` environment variables. 
