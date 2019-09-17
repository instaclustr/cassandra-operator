## Developers Guide
### TODO: Add environment setup, IDE setup, samples on how to run things, etc (maybe?)

#### Tools setup
You will need to ensure your build environment has the following dependencies installed:
* jdk 8
* maven
* docker 
* operator-sdk 0.10
* git
* go 1.13

For running the operator locally, you'll also need minikube or other local k8 environment.


Use any IDE to develop, but also it's recommended to run `go fmt` / `go imports` on your code before committing as we run these checks on PRs and they will fail if the code is not properly formatted.

### Directory structure
It's pretty standard for operator-sdk based projects. The main logic for the controller portion of the Cassandra operator can be found in cmd/ and pkg/ directories and it's written in Go. The sidecar can be found in java/ and is written in Java.

### Model
Defined kubernetes CRD objects can be found in the `controller/cassandradatacenter` module. These are defined by a json schema and there are appropriate types/crds generated when the model/ module is built by the operator-sdk. 

### Backup
The backups are implemented via 2 components: a separate controller in `controller/cassandrabackup` and a sidecar client. The sidecar
client can send operation commands to the sidecar, which then performs the operations on the Cassandra. 

### k8s-addons
This module contains add-on components that extend Cassandra and make it easier to work and operate in a Kubernetes environment.
The k8s-addon jar gets included Cassandra docker image and is dropped into the Cassandra classpath. 

## Building
To build the project, compile the go project, generate images and generate relevant jars, from the project root, run: 
```bash
make
```

This will by default build an images for your local image repo. If you wish to run the operator images (e.g. from the example yaml files or the helm package), you will need to make sure the repo, image and tag all match the docker registry you're using in your environment.

This behavior can be overridden by defining `NO_DEV`, `REGISTRY` and `TAG` environment variables. 
