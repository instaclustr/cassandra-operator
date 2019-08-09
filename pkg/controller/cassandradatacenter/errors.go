package cassandradatacenter

import "errors"

var (
	ErrorCDCNotReady = errors.New("skipping stateful set reconcile, some pods or cassandra nodes are not ready")
)
