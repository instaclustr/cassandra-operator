package cassandradatacenter

import (
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"strings"
)

const (
	rackKey = "cassandra-operator.instaclustr.com/rack"
	cdcKey = "cassandra-operator.instaclustr.com/datacenter"
)

func DataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		cdcKey: cdc.Name,
		"app.kubernetes.io/managed-by":                  "com.instaclustr.cassandra-operator",
	}
}

func AddStatefulSetLabels(labels *map[string]string, rack string) {
	(*labels)[rackKey] = rack
}

func applyDataCenterLabels(labels *map[string]string, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) {
	cdcLabels := DataCenterLabels(cdc)
	for label, val := range cdcLabels {
		(*labels)[label] = val
	}
}

//func applyDataCenterLabels(obj metav1.Object, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) {
//	labels := obj.GetLabels()
//	applyDataCenterLabels(&labels, cdc)
//	obj.SetLabels(labels)
//}

func DataCenterResourceMetadata(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	return metav1.ObjectMeta{
		Namespace: cdc.Namespace,
		Name:      "cassandra-" + cdc.Name + suffix,
		Labels:    DataCenterLabels(cdc),
	}
}

func StatefulSetMetadata(rctx *reconciliationRequestContext, rack string, suffixes ...string) metav1.ObjectMeta {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	labels := DataCenterLabels(rctx.cdc)
	AddStatefulSetLabels(&labels, rack)

	return metav1.ObjectMeta{
		Namespace: rctx.cdc.Namespace,
		Name:      "cassandra-" + rctx.cdc.Name + "-" + rack + suffix,
		Labels:    labels,
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
