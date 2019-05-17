package controller

import (
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	v1 "k8s.io/api/core/v1"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
)

type CassandraOperator interface {
	CreateOrUpdateNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*v1.Service, error)

	CreateOrUpdateSeedNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error)

	CreateOrUpdateOperatorConfigMap(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, seedNodesService *corev1.Service) (*VolumeMount, error)

	CreateOrUpdateStatefulSet(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumeMounts VolumeMounts) (*v1beta2.StatefulSet, error)

	Reconcile(request reconcile.Request) (reconcile.Result, error)
}
