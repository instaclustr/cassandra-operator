package v1alpha1

import (
	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// CassandraDataCenterSpec defines the desired state of CassandraDataCenter
// +k8s:openapi-gen=true
type CassandraDataCenterSpec struct {
	// Cluster is either a string or v1.LocalObjectReference
	//Cluster interface{} `json:"cluster,omitempty"`
	Cluster          string                    `json:"cluster,omitempty"`
	Nodes            int32                     `json:"nodes"`
	CassandraImage   string                    `json:"cassandraImage"`
	SidecarImage     string                    `json:"sidecarImage"`
	ImagePullPolicy  v1.PullPolicy             `json:"imagePullPolicy"`
	ImagePullSecrets []v1.LocalObjectReference `json:"imagePullSecrets,omitempty"`

	Resources v1.ResourceRequirements `json:"resources"`

	DataVolumeClaimSpec v1.PersistentVolumeClaimSpec `json:"dataVolumeClaimSpec"`

	PrometheusSupport bool `json:"prometheusSupport"`
}

// CassandraDataCenterStatus defines the observed state of CassandraDataCenter
// +k8s:openapi-gen=true
type CassandraDataCenterStatus struct {
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraDataCenter is the Schema for the cassandradatacenters API
// +k8s:openapi-gen=true
type CassandraDataCenter struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   CassandraDataCenterSpec   `json:"spec,omitempty"`
	Status CassandraDataCenterStatus `json:"status,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraDataCenterList contains a list of CassandraDataCenter
type CassandraDataCenterList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []CassandraDataCenter `json:"items"`
}

func init() {
	SchemeBuilder.Register(&CassandraDataCenter{}, &CassandraDataCenterList{})
}
