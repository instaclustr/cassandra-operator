package v1alpha1

import (
	v1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// CassandraDataCenterSpec defines the desired state of CassandraDataCenter
// +k8s:openapi-gen=true
type CassandraDataCenterSpec struct {
	Nodes                     int32                         `json:"nodes,omitempty"`
	CassandraImage            string                        `json:"cassandraImage,omitempty"`
	SidecarImage              string                        `json:"sidecarImage,omitempty"`
	Racks                     []Rack                        `json:"racks,omitempty"`
	ImagePullPolicy           v1.PullPolicy                 `json:"imagePullPolicy,omitempty"`
	ImagePullSecrets          []v1.LocalObjectReference     `json:"imagePullSecrets,omitempty"`
	UserSecretVolumeSource    *v1.SecretVolumeSource        `json:"userSecretVolumeSource,omitempty"`
	UserConfigMapVolumeSource *v1.ConfigMapVolumeSource     `json:"userConfigMapVolumeSource,omitempty"`
	Resources                 *v1.ResourceRequirements      `json:"resources,omitempty"`
	SidecarResources          *v1.ResourceRequirements      `json:"sidecarResources,omitempty"`
	DummyVolume               *v1.EmptyDirVolumeSource      `json:"dummyVolume,omitempty"`
	DeletePVCs                bool                          `json:"deletePVCs,omitempty"`
	DataVolumeClaimSpec       *v1.PersistentVolumeClaimSpec `json:"dataVolumeClaimSpec,omitempty"`
	OptimizeKernelParams      bool                          `json:"optimizeKernelParams,omitempty"`
	PrometheusSupport         bool                          `json:"prometheusSupport,omitempty"`
	OperatorLabels            *OperatorLabels               `json:"operatorLabels,omitempty"`
	SidecarEnv                []v1.EnvVar                   `json:"sidecarEnv,omitempty"`
	CassandraEnv              []v1.EnvVar                   `json:"cassandraEnv,omitempty"`
	ServiceAccountName        string                        `json:"serviceAccountName,omitempty"`
	FSGroup                   int64                         `json:"fsGroup,omitempty"`
	Backup                    Backup                        `json:"backup,omitempty"`
}

// CassandraDataCenterStatus defines the observed state of CassandraDataCenter
// +k8s:openapi-gen=true
type CassandraDataCenterStatus struct {
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CassandraDataCenter is the Schema for the cassandradatacenters API
// +k8s:openapi-gen=true
// +kubebuilder:subresource:status
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

type Rack struct {
	Name   string            `json:"name"`
	Labels map[string]string `json:"labels"`
}

type OperatorLabels struct {
	PrometheusService map[string]string `json:"prometheusService,omitempty"`
	NodesService      map[string]string `json:"nodesService,omitempty"`
	SeedNodesService  map[string]string `json:"seedNodesService,omitempty"`
	StatefulSet       map[string]string `json:"statefulSet,omitempty"`
	PodTemplate       map[string]string `json:"podTemplate,omitempty"`
}

type Backup struct {
	BackupName               string                 `json:"backupName,omitempty"`
	Restore                  bool                   `json:"restore,omitempty"`
	BackupSecretVolumeSource *v1.SecretVolumeSource `json:"backupSecretVolumeSource,omitempty"`
}
