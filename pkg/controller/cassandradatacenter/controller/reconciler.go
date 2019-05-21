package controller

import (
	"context"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
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

// Reconcile reads that state of the cluster for a CassandraDataCenter object and makes changes based on the state read
// and what is in the CassandraDataCenter.Spec
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (reconciler *CassandraDataCenterReconciler) Reconcile(request reconcile.Request) (reconcile.Result, error) {

	logReconcileRequest(request)

	// Fetch the CassandraDataCenter instance

	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	err := reconciler.client.Get(context.TODO(), request.NamespacedName, cdc)

	if err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	nodesService, err := CreateOrUpdateNodesService(reconciler, cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	seedNodesService, err := CreateOrUpdateSeedNodesService(reconciler, cdc)
	if err != nil {
		return reconcile.Result{}, err
	}

	configMapVolumeMount, err := CreateOrUpdateOperatorConfigMap(reconciler, cdc, seedNodesService)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := CreateOrUpdateStatefulSet(reconciler, cdc, VolumeMounts{configMapVolumeMount})
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
