package controller

import (
	"context"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
)

func CreateOrUpdateNodesService(reconciler *CassandraDataCenterReconciler, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {

	nodesService := &corev1.Service{
		ObjectMeta: DataCenterResourceMetadata(cdc, "nodes"),
	}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), reconciler.client, nodesService, func(_ runtime.Object) error {
		nodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports: []corev1.ServicePort{
				{
					Name: "cql",
					Port: CassandraCqlPort,
				},
				{
					Name: "jmx",
					Port: CassandraJMXPort,
				},
			},
			Selector: DataCenterLabels(cdc),
		}

		if cdc.Spec.PrometheusSupport {
			nodesService.Spec.Ports = append(nodesService.Spec.Ports, corev1.ServicePort{Name: "prometheus", Port: CassandraPrometheusPort})
		}

		if err := controllerutil.SetControllerReference(cdc, nodesService, reconciler.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	return nodesService, err
}
