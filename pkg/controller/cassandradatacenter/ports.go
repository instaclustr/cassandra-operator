package cassandradatacenter

import (
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/core/v1"
)

var internodePort = port{"internode", 7000}
var internodeTlsPort = port{"internode-tls", 7001}
var prometheusPort = port{"prometheus", 9500}
var cqlPort = port{"cql", 9042}
var jmxPort = port{"jmx", 7199}
var promqlPort = port{"promql", 9500}
var sidecarPort = port{"http", sidecar.DefaultSidecarClientOptions.Port}

type port struct {
	name string
	port int32
}

type ports []port

func (port port) asContainerPort() v1.ContainerPort {
	return v1.ContainerPort{Name: port.name, ContainerPort: port.port}
}

func (port port) asContainerPorts() []v1.ContainerPort {
	return []v1.ContainerPort{port.asContainerPort()}
}

func (port port) asServicePort() v1.ServicePort {
	return v1.ServicePort{Name: port.name, Port: port.port}
}

func (port port) asServicePorts() []v1.ServicePort {
	return []v1.ServicePort{port.asServicePort()}
}

func (ports ports) asContainerPorts() []v1.ContainerPort {
	var containerPorts []v1.ContainerPort

	for _, p := range ports {
		containerPorts = append(containerPorts, p.asContainerPort())
	}

	return containerPorts
}

func (ports ports) asServicePorts() []v1.ServicePort {

	var servicePorts []v1.ServicePort

	for _, p := range ports {
		servicePorts = append(servicePorts, p.asServicePort())
	}

	return servicePorts
}
