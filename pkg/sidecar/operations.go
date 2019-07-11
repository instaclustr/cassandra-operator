package sidecar

import (
	"encoding/json"
	"github.com/google/uuid"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"time"
)

//go:generate jsonenums -type=Kind
type Kind int

// Operations

const (
	noop Kind = iota
	cleanup
	upgradesstables
	decommission
	backup
	rebuild
	scrub
)

func GetType(s string) Kind {
	kind, ok := _KindNameToValue[s]
	if !ok {
		return noop
	}
	return kind
}

type OperationRequest interface {
	Init()
}

type Operation struct {
	Type Kind `json:"type"`
}

type DecommissionRequest struct {
	Operation
}

func (d *DecommissionRequest) Init() {
	d.Type = decommission
}

type CleanupRequest struct {
	Operation
	Jobs     int32    `json:"jobs,omitempty"`
	Tables   []string `json:"tables,omitempty"`
	Keyspace string   `json:"keyspace"`
}

func (c *CleanupRequest) Init() {
	c.Type = cleanup
}

type BackupRequest struct {
	Operation
	DestinationUri string   `json:"destinationUri"`
	Keyspaces      []string `json:"keyspaces"`
	SnapshotName   string   `json:"snapshotName"`
}

func (b *BackupRequest) Init() {
	b.Type = backup
}

type UpgradeSSTablesRequest struct {
	Operation
	IncludeAllSSTables bool     `json:"includeAllSSTables,omitempty"`
	Jobs               int32    `json:"jobs,omitempty"`
	Tables             []string `json:"tables,omitempty"`
	Keyspace           string   `json:"keyspace"`
}

func (u *UpgradeSSTablesRequest) Init() {
	u.Type = upgradesstables
}

type TokenRange struct {
	Start string `json:"start"`
	End   string `json:"end"`
}

type RebuildRequest struct {
	Operation
	SourceDC        string       `json:"sourceDC,omitempty"`
	Keyspace        string       `json:"keyspace"`
	SpecificSources []string     `json:"specificSources,omitempty"`
	SpecificTokens  []TokenRange `json:"specificTokens,omitempty"`
}

func (r *RebuildRequest) Init() {
	r.Type = rebuild
}

type ScrubRequest struct {
	Operation
	DisableSnapshot       bool     `json:"disableSnapshot,omitempty"`
	SkipCorrupted         bool     `json:"skipCorrupted,omitempty"`
	NoValidate            bool     `json:"noValidate,omitempty"`
	ReinsertOverflowedTTL bool     `json:"reinsertOverflowedTTL,omitempty"`
	Jobs                  int32    `json:"jobs,omitempty"`
	Tables                []string `json:"tables,omitempty"`
	Keyspace              string   `json:"keyspace"`
}

func (s *ScrubRequest) Init() {
	s.Type = scrub
}

type OperationResponse map[string]interface{}
type Operations []OperationResponse

type BasicResponse struct {
	Id             uuid.UUID                 `json:"id"`
	CreationTime   time.Time                 `json:"creationTime"`
	State          operations.OperationState `json:"state"`
	Progress       float32                   `json:"progress"`
	StartTime      time.Time                 `json:"startTime"`
	CompletionTime time.Time                 `json:"completionTime"`
}

// decommission Operations
type DecommissionOperationResponse struct {
	BasicResponse
	DecommissionRequest
}

// cleanup Operations
type CleanupOperationResponse struct {
	BasicResponse
	CleanupRequest
}

func (c *CleanupOperationResponse) String() string {
	op, _ := json.Marshal(c)
	return string(op)
}

func (client *Client) ListCleanups() ([]*CleanupOperationResponse, error) {

	ops, err := client.GetOperations()
	if ops == nil || err != nil {
		return []*CleanupOperationResponse{}, err
	}

	operations, err := FilterOperations(*ops, cleanup)
	if err != nil {
		return []*CleanupOperationResponse{}, err
	}

	var cleanups []*CleanupOperationResponse
	for _, op := range operations {
		cleanups = append(cleanups, op.(*CleanupOperationResponse))
	}

	return cleanups, nil
}

// backup Operations
type BackupResponse struct {
	BasicResponse
	BackupRequest
}

func (b *BackupResponse) String() string {
	op, _ := json.Marshal(b)
	return string(op)
}

func (client *Client) ListBackups() ([]*BackupResponse, error) {

	ops, err := client.GetOperations()
	if ops == nil || err != nil {
		return []*BackupResponse{}, err
	}

	operations, err := FilterOperations(*ops, backup)
	if err != nil {
		return []*BackupResponse{}, err

	}

	var backups []*BackupResponse
	for _, op := range operations {
		backups = append(backups, op.(*BackupResponse))
	}

	return backups, nil
}

// UpgradeSSTables
type UpgradeSSTablesResponse struct {
	BasicResponse
	UpgradeSSTablesRequest
}

// rebuild
type RebuildResponse struct {
	BasicResponse
	RebuildRequest
}

// scrub
type ScrubResponse struct {
	BasicResponse
	ScrubRequest
}
