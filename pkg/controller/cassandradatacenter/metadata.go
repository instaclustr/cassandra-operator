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
