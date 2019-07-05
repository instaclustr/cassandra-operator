package nodestate

/// NodeStates

type NodeState string

const (
	STARTING       = NodeState("STARTING")
	NORMAL         = NodeState("NORMAL")
	JOINING        = NodeState("JOINING")
	LEAVING        = NodeState("LEAVING")
	DECOMMISSIONED = NodeState("DECOMMISSIONED")
	MOVING         = NodeState("MOVING")
	DRAINING       = NodeState("DRAINING")
	DRAINED        = NodeState("DRAINED")
	ERROR          = NodeState("ERROR")
)
type Status struct {
	NodeState NodeState `json:"nodeState"`
}

