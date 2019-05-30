package controller

import v1 "k8s.io/api/core/v1"

type port struct {
	name string
	port int32
}

var cassandraJmxPort = port{"jmx", 7199}

func asContainerPort(port port) v1.ContainerPort {
	return v1.ContainerPort{Name: port.name, ContainerPort: port.port}
}

func asServicePort(port port) v1.ServicePort {
	return v1.ServicePort{Name: port.name, Port: port.port}
}
