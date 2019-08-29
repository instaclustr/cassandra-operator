package cassandradatacenter

import "errors"

var (
	ErrorClusterNotReady = errors.New("skipping stateful set reconcile, some pods or cassandra nodes are not ready")
)
