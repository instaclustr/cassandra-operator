package e2e

import (
	"errors"

	cop "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"

	v1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"os"
)

type DcMetaInfo struct {
	Name               string
	Datacenter         string
	Cluster            string
	CassandraImageName string
	SidecarImageName   string
	Disk               *resource.Quantity
	Memory             *resource.Quantity
}

func parseEnvProperty(name string, defaultValue string) string {

	value := os.Getenv(name)
	if value == "" {
		return defaultValue
	}

	return value
}

func (info *DcMetaInfo) validate() error {
	if len(info.Datacenter) == 0 {
		return errors.New("datacenter not set")
	}

	if len(info.Cluster) == 0 {
		return errors.New("cluster not set")
	}

	if info.Disk == nil {
		return errors.New("disk not set")
	}

	if info.Memory == nil {
		return errors.New("memory not set")
	}

	repository := parseEnvProperty("OPERATOR_TEST_DOCKER_REPOSITORY", "gcr.io/cassandra-operator/")

	if len(info.CassandraImageName) == 0 {
		info.CassandraImageName = parseEnvProperty("OPERATOR_TEST_CASSANDRA_IMAGE", repository+"cassandra-3.11.6:latest")
	}

	if len(info.SidecarImageName) == 0 {
		info.SidecarImageName = parseEnvProperty("OPERATOR_TEST_SIDECAR_IMAGE", repository+"sidecar:latest")
	}

	return nil
}

func (info *DcMetaInfo) resolveDcMetadataName() string {

	if len(info.Name) != 0 {
		return info.Name
	}

	return "cassandra-" + info.Cluster + "-" + info.Datacenter
}

//
// CassandraDataCenter
//

func defaultCassandraDataCenter(nodes int32, info *DcMetaInfo) *cop.CassandraDataCenter {

	spec := cop.CassandraDataCenterSpec{
		Nodes:             nodes,
		CassandraImage:    info.CassandraImageName,
		SidecarImage:      info.SidecarImageName,
		PrometheusSupport: false,
		ImagePullPolicy:   corev1.PullAlways,
		DummyVolume: &corev1.EmptyDirVolumeSource{
			Medium:    "",
			SizeLimit: info.Disk,
		},
		Resources: &corev1.ResourceRequirements{
			Limits: corev1.ResourceList{
				"memory": *info.Memory,
			},
			Requests: corev1.ResourceList{
				"memory": *info.Memory,
			},
		},
		FSGroup: 999,
	}

	return buildDataCenter(info, spec)
}

func buildDataCenter(info *DcMetaInfo, spec cop.CassandraDataCenterSpec) *cop.CassandraDataCenter {
	cdc := &cop.CassandraDataCenter{
		TypeMeta: v1.TypeMeta{
			APIVersion: "cassandraoperator.instaclustr.com/v1alpha1",
			Kind:       "CassandraDataCenter",
		},
		ObjectMeta: v1.ObjectMeta{
			Name: info.resolveDcMetadataName(),
		},
		Cluster:    info.Cluster,
		DataCenter: info.Datacenter,
		Spec:       spec,
		Status:     cop.CassandraDataCenterStatus{},
	}

	addImagePullSecrets(cdc)

	return cdc
}

func addImagePullSecrets(cdc *cop.CassandraDataCenter) *cop.CassandraDataCenter {
	pullSecret := parseEnvProperty("OPERATOR_TEST_PULL_SECRET", "")

	if pullSecret != "" {
		cdc.Spec.ImagePullSecrets = []corev1.LocalObjectReference{
			{
				Name: pullSecret,
			},
		}
	}

	return cdc
}

//
// CassandraCluster
//

func buildCluster() cop.CassandraCluster {
	return cop.CassandraCluster{
		TypeMeta:   v1.TypeMeta{},
		ObjectMeta: v1.ObjectMeta{},
		Spec:       cop.CassandraClusterSpec{},
		Status:     cop.CassandraClusterStatus{},
	}
}

//
// ConfigMaps
//

func buildDefaultConfigMap(info *DcMetaInfo) *corev1.ConfigMap {
	return &corev1.ConfigMap{
		ObjectMeta: metav1.ObjectMeta{
			Name: "cassandra-operator-default-config",
		},
		Data: map[string]string{
			"nodes":          "1",
			"cassandraImage": info.CassandraImageName,
			"sidecarImage":   info.SidecarImageName,
			"memory":         info.Memory.String(),
			"disk":           info.Disk.String(),
		},
	}
}

func buildConfigMaps(info *DcMetaInfo) []*corev1.ConfigMap {
	return []*corev1.ConfigMap{
		buildDefaultConfigMap(info),
	}
}

//
// CassandraDataCenterList
//

func defaultNewCassandraDataCenterList() *cop.CassandraDataCenterList {
	return &cop.CassandraDataCenterList{
		TypeMeta: metav1.TypeMeta{
			Kind:       "CassandraDataCenter",
			APIVersion: "cassandraoperator.instaclustr.com/v1alpha1",
		},
	}
}

func statefulSetName(cdc *cop.CassandraDataCenter, rack *cluster.Rack) string {
	return "cassandra-" + cdc.Cluster + "-" + cdc.DataCenter + "-" + rack.Name
}
