package controller

import (
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"strings"
)

func DataCenterLabels(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) map[string]string {
	return map[string]string{
		"cassandra-operator.instaclustr.com/datacenter": cdc.Name,
		"app.kubernetes.io/managed-by":                  "com.instaclustr.cassandra-operator",
	}
}

func applyDataCenterLabels(labels *map[string]string, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) {
	(*labels)["cassandra-operator.instaclustr.com/datacenter"] = cdc.Name
	(*labels)["app.kubernetes.io/managed-by"] = "com.instaclustr.cassandra-operator"
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

func applyDataCenterResourceMetadata(obj metav1.Object, cdc *cassandraoperatorv1alpha1.CassandraDataCenter, suffixes ...string) {
	suffix := strings.Join(append([]string{""}, suffixes...), "-")

	obj.SetNamespace(cdc.Namespace)
	obj.SetName("cassandra-" + cdc.Name + suffix)

	labels := obj.GetLabels()
	applyDataCenterLabels(&labels, cdc)
	obj.SetLabels(labels)
}