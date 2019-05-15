package controller

import (
	"context"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	v1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
)

var log = logf.Log.WithName("CassandraDataCenterReconciler")

// CassandraDataCenterReconciler reconciles a CassandraDataCenter object
type CassandraDataCenterReconciler struct {
	// This client is a split client that reads objects from the cache and writes to the apiserver
	client client.Client
	scheme *runtime.Scheme
}

func (reconciler *CassandraDataCenterReconciler) CreateOrUpdateNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*v1.Service, error) {
	return CreateOrUpdateNodesService(reconciler, cdc)
}

func (reconciler *CassandraDataCenterReconciler) GetClient() client.Client {
	return reconciler.client
}

func (reconciler *CassandraDataCenterReconciler) GetScheme() *runtime.Scheme {
	return reconciler.scheme
}

func (reconciler *CassandraDataCenterReconciler) SetClient(client client.Client) {
	reconciler.client = client
}

func (reconciler *CassandraDataCenterReconciler) SetScheme(scheme *runtime.Scheme) {
	reconciler.scheme = scheme
}

func (reconciler *CassandraDataCenterReconciler) CreateOrUpdateSeedNodesService(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*corev1.Service, error) {
	return CreateOrUpdateSeedNodesService(reconciler, cdc)
}

func (reconciler *CassandraDataCenterReconciler) CreateOrUpdateOperatorConfigMap(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, seedNodesService *corev1.Service) (*VolumeMount, error) {
	return CreateOrUpdateOperatorConfigMap(reconciler, cdc, seedNodesService)
}

func (reconciler *CassandraDataCenterReconciler) CreateOrUpdateStatefulSet(cdc *cassandraoperatorv1alpha1.CassandraDataCenter, volumeMounts VolumeMounts) (*v1beta2.StatefulSet, error) {
	return CreateOrUpdateStatefulSet(reconciler, cdc, volumeMounts)
}

// Reconcile reads that state of the cluster for a CassandraDataCenter object and makes changes based on the state read
// and what is in the CassandraDataCenter.Spec
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (reconciler *CassandraDataCenterReconciler) Reconcile(request reconcile.Request) (reconcile.Result, error) {

	logReconcileRequest(request)

	// Fetch the CassandraDataCenter instance

	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	err := reconciler.GetClient().Get(context.TODO(), request.NamespacedName, cdc)

	if err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	nodesService, err := reconciler.CreateOrUpdateNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	seedNodesService, err := reconciler.CreateOrUpdateSeedNodesService(cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	configMapVolumeMount, err := reconciler.CreateOrUpdateOperatorConfigMap(cdc, seedNodesService)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := reconciler.CreateOrUpdateStatefulSet(cdc, VolumeMounts{configMapVolumeMount})
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	return reconcile.Result{}, nil
}

func logReconcileRequest(request reconcile.Request) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling CassandraDataCenter")
}
