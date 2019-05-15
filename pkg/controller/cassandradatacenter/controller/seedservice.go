package controller

import (
	"context"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
)

func CreateOrUpdateSeedNodesService(reconciler *CassandraDataCenterReconciler, cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {

	objectMetadata := DataCenterResourceMetadata(cdc, "seeds")

	objectMetadata.Annotations = map[string]string{
		"service.alpha.kubernetes.io/tolerate-unready-endpoints": "true",
	}

	seedNodesService := &corev1.Service{
		ObjectMeta: objectMetadata,
	}

	_, err := controllerutil.CreateOrUpdate(context.TODO(), reconciler.client, seedNodesService, func(_ runtime.Object) error {
		seedNodesService.Spec = corev1.ServiceSpec{
			ClusterIP: "None",
			Ports: []corev1.ServicePort{
				{
					Name: "internode",
					Port: CassandraInternodePort,
				},
			},
			Selector:                 DataCenterLabels(cdc),
			PublishNotReadyAddresses: true,
		}

		if err := controllerutil.SetControllerReference(cdc, seedNodesService, reconciler.scheme); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return nil, err
	}

	return seedNodesService, err
}
