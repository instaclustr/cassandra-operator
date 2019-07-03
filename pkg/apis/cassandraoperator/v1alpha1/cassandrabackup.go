package v1alpha1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// CassandraBackupSpec defines the desired state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupSpec struct {
	// The uri for the backup target location e.g. s3 bucket, filepath
	DestinationUri string `json:"destinationUri"`
	// The list of keyspaces to back up
	Keyspaces    []string `json:"keyspaces"`
	SnapshotName string   `json:"snapshotName"`
}

// CassandraBackupStatus defines the observed state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupStatus struct {
	// State shows the status of the operation
	State string `json:"state"`
	// Progress shows the percentage of the operation done
	Progress float32 `json:"progress"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraBackup is the Schema for the cassandrabackups API
// +k8s:openapi-gen=true
// +kubebuilder:printcolumn:name="State",type="string",JSONPath=".status.state",description="Backup operation state"
// +kubebuilder:printcolumn:name="Progress",type="number",JSONPath=".status.progress",description="Backup operation progress"
// TODO: apply state/progress appropriately for every node, not the whole operation
type CassandraBackup struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   CassandraBackupSpec   `json:"spec,omitempty"`
	Status CassandraBackupStatus `json:"status,omitempty"`
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
