package sidecar

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
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

type OperationsFilter struct {
	Types  []Kind
	States []operations.OperationState
}

func (o OperationsFilter) buildFilteredEndpoint(endpoint string) string {
	var filterT, filterS string
	if len(o.Types) > 0 {
		var kinds []string
		for _, kind := range o.Types {
			kinds = append(kinds, _KindValueToName[kind])
		}
		filterT = "type=" + strings.Join(kinds, ",")
	}

	if len(o.States) > 0 {
		var states []string
		for _, state := range o.States {
			states = append(states, string(state))
		}
		filterS = "state=" + strings.Join(states, ",")
	}

	filter := strings.Join([]string{filterT, filterS}, "&")
	if len(filter) > 0 {
		return endpoint + "?" + filter
	}

	return endpoint

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
	StorageLocation       string `json:"storageLocation"`
	SnapshotTag           string `json:"snapshotTag,omitempty"`
	ConcurrentConnections int    `json:"concurrentConnections,omitempty"`
	Entities              string `json:"entities,omitempty"`
	Secret                string `json:"k8sSecretName"`
	KubernetesNamespace   string `json:"k8sNamespace"`
	GlobalRequest         bool   `json:"globalRequest,omitempty"`
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
	var cleanups []*CleanupOperationResponse
	ops, err := client.GetFilteredOperations(&OperationsFilter{Types: []Kind{cleanup}})
	if ops == nil || err != nil {
		return cleanups, err
	}

	for _, op := range *ops {
		cleanOp, err := ParseOperation(op, cleanup)
		if err != nil {
			return []*CleanupOperationResponse{}, err
		}
		cleanups = append(cleanups, cleanOp.(*CleanupOperationResponse))
	}

	return cleanups, nil
}

func (client *Client) FindBackup(id uuid.UUID) (backupResponse *BackupResponse, err error) {
	if op, err := client.GetOperation(id); err != nil {
		return nil, err
	} else if b, err := ParseOperation(*op, backup); err != nil {
		return nil, err
	} else if backupResponse, ok := b.(*BackupResponse); !ok {
		return nil, fmt.Errorf("can't parse operation to backup")
	} else {
		return backupResponse, nil
	}
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

	backupOps, err := FilterOperations(*ops, backup)
	if err != nil {
		return []*BackupResponse{}, err

	}

	var backups []*BackupResponse
	for _, op := range backupOps {
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
