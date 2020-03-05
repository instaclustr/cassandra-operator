package cassandradatacenter

import (
	"strings"

	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"

	cop "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

const (
	ManagedByKey   = "app.kubernetes.io/managed-by"
	ManagedByValue = "com.instaclustr.cassandra-operator"
	RackKey        = "cassandra-operator.instaclustr.com/rack"
	DataCenterKey  = "cassandra-operator.instaclustr.com/datacenter"
	ClusterKey     = "cassandra-operator.instaclustr.com/cluster"
)

// ANNOTATIONS

func DataCenterAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	return map[string]string{
		DataCenterKey: cdc.DataCenter,
		ClusterKey:    cdc.Cluster,
		ManagedByKey:  ManagedByValue,
	}
}

func SeedNodesAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorAnnotations != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.SeedNodesService
	}

	return objectAnnotations
}

func NodesServiceAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorAnnotations != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.NodesService
	}

	return objectAnnotations
}

func PodTemplateSpecAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorAnnotations != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.PodTemplate
	}

	return objectAnnotations
}

func PrometheusAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	// Fetch cdc annotations
	annotations := DataCenterAnnotations(cdc)
	// Add prometheus annotations if defined
	if cdc.Spec.OperatorAnnotations != nil {
		for label, val := range cdc.Spec.OperatorAnnotations.PrometheusService {
			annotations[label] = val
		}
	}

	return annotations
}

func CustomStatefulSetAnnotations(cdc *cop.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorAnnotations != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.StatefulSet
	}

	return objectAnnotations
}

// LABELS

func DataCenterLabels(cdc *cop.CassandraDataCenter) map[string]string {
	return map[string]string{
		DataCenterKey: cdc.DataCenter,
		ClusterKey:    cdc.Cluster,
		ManagedByKey:  ManagedByValue,
	}
}

func SeedNodesLabels(cdc *cop.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.SeedNodesService
	}

	return objectLabels
}

func NodesServiceLabels(cdc *cop.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.NodesService
	}

	return objectLabels
}

func PodTemplateSpecLabels(cdc *cop.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.PodTemplate
	}

	return objectLabels
}

func PrometheusLabels(cdc *cop.CassandraDataCenter) map[string]string {
	// Fetch cdc labels
	labels := DataCenterLabels(cdc)
	// Add prometheus labels if defined

	if cdc.Spec.OperatorLabels != nil {
		for label, val := range cdc.Spec.OperatorLabels.PrometheusService {
			labels[label] = val
		}
	}

	return labels
}

func RackLabels(cdc *cop.CassandraDataCenter, rack *cluster.Rack) map[string]string {
	// Fetch cdc labels
	rackLabels := DataCenterLabels(cdc)
	// Add rack info
	rackLabels[RackKey] = rack.Name
	return rackLabels
}

// METADATA

func DataCenterResourceMetadata(cdc *cop.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	objectMeta := metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Cluster + "-" + cdc.DataCenter + suffix,
		Labels:    DataCenterLabels(cdc),
	}

	return objectMeta
}

func CustomStatefulSetLabels(cdc *cop.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.StatefulSet
	}

	return objectLabels
}

func RackMetadata(cdc *cop.CassandraDataCenter, rack *cluster.Rack, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")
	objectMeta := metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Cluster + "-" + cdc.DataCenter + suffix + "-" + rack.Name,
		Labels:    RackLabels(cdc, rack),
	}

	return objectMeta
}

func StatefulSetMetadata(cdc *cop.CassandraDataCenter, objectMetaData metav1.ObjectMeta) metav1.ObjectMeta {
	objectMetaData.Labels = mergeLabelMaps(objectMetaData.Labels, CustomStatefulSetLabels(cdc))
	objectMetaData.Annotations = mergeLabelMaps(objectMetaData.Annotations, CustomStatefulSetAnnotations(cdc))
	return objectMetaData
}

func mergeLabelMaps(firstLabels, secondLabels map[string]string) map[string]string {

	var mergedLabels = map[string]string{}

	for k, v := range firstLabels {
		mergedLabels[k] = v
	}

	for k, v := range secondLabels {
		mergedLabels[k] = v
	}

	return mergedLabels
}
