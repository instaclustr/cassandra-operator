package v1alpha1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// CassandraBackupSpec defines the desired state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupSpec struct {
}

// CassandraBackupStatus defines the observed state of CassandraBackup
// +k8s:openapi-gen=true
type CassandraBackupStatus struct {
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraBackup is the Schema for the cassandrabackups API
// +k8s:openapi-gen=true
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
