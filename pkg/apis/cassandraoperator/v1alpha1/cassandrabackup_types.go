package v1alpha1

import (
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// CassandraBackupSpec defines the desired state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupSpec struct {
	// Cassandra DC name to back up. Used to find the pods in the CDC
	CDC string `json:"cdc"`
	// The uri for the backup target location e.g. s3 bucket, filepath
	StorageLocation string `json:"storageLocation"`
	// The snapshot tag for the backup
	SnapshotTag           string `json:"snapshotTag"`
	Duration              string `json:"duration,omitempty"`
	Bandwidth             string `json:"bandwidth,omitempty"`
	ConcurrentConnections int    `json:"concurrentConnections,omitempty"`
	Table                 string `json:"table,omitempty"`
	// The list of keyspaces to back up
	Keyspaces []string `json:"keyspaces,omitempty"`
}

// CassandraBackupStatus defines the observed state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupStatus struct {
	// name of pod / node
	Node string `json:"node"`
	// State shows the status of the operation
	State operations.OperationState `json:"state"`
	// Progress shows the percentage of the operation done
	Progress string `json:"progress"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraBackup is the Schema for the cassandrabackups API
// +k8s:openapi-gen=true
// +kubebuilder:printcolumn:name="Status",type="string",JSONPath=".globalStatus",description="Backup operation status"
// +kubebuilder:printcolumn:name="Progress",type="string",JSONPath=".globalProgress",description="Backup operation progress"
type CassandraBackup struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec           CassandraBackupSpec       `json:"spec,omitempty"`
	Status         []*CassandraBackupStatus  `json:"status,omitempty"`
	GlobalStatus   operations.OperationState `json:"globalStatus,omitempty"`
	GlobalProgress string                    `json:"globalProgress,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraBackupList contains a list of CassandraBackup
type CassandraBackupList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []CassandraBackup `json:"items"`
}

func init() {
	SchemeBuilder.Register(&CassandraBackup{}, &CassandraBackupList{})
}
