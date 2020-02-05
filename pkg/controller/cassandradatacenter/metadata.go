package cassandradatacenter

import (
	"strings"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/cluster"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

const (
	rackKey = "cassandra-operator.instaclustr.com/rack"
	cdcKey  = "cassandra-operator.instaclustr.com/datacenter"
)

// ANNOTATIONS

func DataCenterAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		cdcKey:                         cdc.Name,
		"app.kubernetes.io/managed-by": "com.instaclustr.cassandra-operator",
	}
}

func SeedNodesAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.SeedNodesService
	}

	return objectAnnotations
}

func NodesServiceAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.NodesService
	}

	return objectAnnotations
}

func PodTemplateSpecAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.PodTemplate
	}

	return objectAnnotations
}

func PrometheusAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
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

func CustomStatefulSetAnnotations(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectAnnotations = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectAnnotations = cdc.Spec.OperatorAnnotations.StatefulSet
	}

	return objectAnnotations
}

// LABELS

func DataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		cdcKey:                         cdc.Name,
		"app.kubernetes.io/managed-by": "com.instaclustr.cassandra-operator",
	}
}

func SeedNodesLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.SeedNodesService
	}

	return objectLabels
}

func NodesServiceLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.NodesService
	}

	return objectLabels
}

func PodTemplateSpecLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.PodTemplate
	}

	return objectLabels
}

func PrometheusLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
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

func RackLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, rack *cluster.Rack) map[string]string {
	// Fetch cdc labels
	rackLabels := DataCenterLabels(cdc)
	// Add rack info
	rackLabels[rackKey] = rack.Name
	return rackLabels
}

// METADATA

func DataCenterResourceMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Name + suffix,
		Labels:    DataCenterLabels(cdc),
	}
}

func CustomStatefulSetLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels = map[string]string{}

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.StatefulSet
	}

	return objectLabels
}

func RackMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, rack *cluster.Rack, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")
	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Name + suffix + "-" + rack.Name,
		Labels:    RackLabels(cdc, rack),
	}
}

func StatefulSetMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, objectMetaData metav1.ObjectMeta) metav1.ObjectMeta {
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
