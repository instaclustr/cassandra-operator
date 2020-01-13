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

func DataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		cdcKey:                         cdc.Name,
		"app.kubernetes.io/managed-by": "com.instaclustr.cassandra-operator",
	}
}

func operatorObjectLabel(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, objectLabels map[string]string) map[string]string {

	// Fetch cdc labels
	labels := DataCenterLabels(cdc)

	for label, val := range objectLabels {
		labels[label] = val
	}

	return labels
}

func SeedNodesLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels map[string]string

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.SeedNodesService
	}

	return operatorObjectLabel(cdc, objectLabels)
}

func NodesServiceLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels map[string]string

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.NodesService
	}

	return operatorObjectLabel(cdc, objectLabels)
}

func StatefulsetLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels map[string]string

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.StatefulSet
	}

	return operatorObjectLabel(cdc, objectLabels)
}

func PrometheusLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels map[string]string

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.PrometheusService
	}

	return operatorObjectLabel(cdc, objectLabels)
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

func PodTemplateSpecLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	var objectLabels map[string]string

	if cdc.Spec.OperatorLabels != nil {
		objectLabels = cdc.Spec.OperatorLabels.PodTemplate
	}

	return operatorObjectLabel(cdc, objectLabels)
}

func RackLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, rack *cluster.Rack) map[string]string {
	// Fetch cdc labels
	rackLabels := DataCenterLabels(cdc)
	// Add rack info
	rackLabels[rackKey] = rack.Name
	return rackLabels
}

func applyDataCenterLabels(labels *map[string]string, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) {
	cdcLabels := DataCenterLabels(cdc)
	for label, val := range cdcLabels {
		(*labels)[label] = val
	}
}

func DataCenterResourceMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Name + suffix,
		Labels:    DataCenterLabels(cdc),
	}
}

func RackMetadata(rctx *reconciliationRequestContext, rack *cluster.Rack, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")
	return metav1.ObjectMeta{
		Namespace: rctx.cdc.Namespace,
		Name:      "cassandra-" + rctx.cdc.Name + suffix + "-" + rack.Name,
		Labels:    RackLabels(rctx.cdc, rack),
	}
}

func applyDataCenterResourceMetadata(obj metav1.Object, cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	obj.SetNamespace(cdc.Namespace)
	obj.SetName("cassandra-" + cdc.Name + suffix)

	labels := obj.GetLabels()
	applyDataCenterLabels(&labels, cdc)
	obj.SetLabels(labels)
}
