package controller

import (
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandrabackup"
)

func init() {
	// AddToManagerFuncs is a list of functions to create controllers and add them to a manager.
	AddToManagerFuncs = append(AddToManagerFuncs, cassandrabackup.Add)
}
