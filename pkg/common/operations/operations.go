package operations

type OperationState string

const (
	RUNNING   = OperationState("RUNNING")
	PENDING   = OperationState("PENDING")
	COMPLETED = OperationState("COMPLETED")
	FAILED    = OperationState("FAILED")
	UNKNOWN   = OperationState("UNKNOWN")
)
