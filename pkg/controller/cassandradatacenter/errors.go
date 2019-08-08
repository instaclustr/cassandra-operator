package cassandradatacenter

import "errors"

var (
	ErrorCDCNotReady = errors.New("skipping stateful set reconcile, some stateful sets are not ready")
	ErrorRackPods = errors.New("unable to list pods in a rack")
	ErrorCDCPods = errors.New("unable to list pods in a cdc")
)
