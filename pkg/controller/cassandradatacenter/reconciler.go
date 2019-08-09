package cassandradatacenter

import (
	"context"
	"github.com/go-logr/logr"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
)

const (
	controllerName = "CassandraDataCenterController"
)

var log = logf.Log.WithName(controllerName)

// CassandraDataCenterReconciler reconciles a CassandraDataCenter object
type CassandraDataCenterReconciler struct {
	// This client is a split client that reads objects from the cache and writes to the apiserver
	client client.Client
	scheme *runtime.Scheme
}

type reconciliationRequestContext struct {
	CassandraDataCenterReconciler
	reconcile.Request
	cdc    *cassandraoperatorv1alpha1.CassandraDataCenter
	logger logr.Logger
}

// Reconcile reads that state of the cluster for a CassandraDataCenter object and makes changes based on the state read
// and what is in the CassandraDataCenter.Spec
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (reconciler *CassandraDataCenterReconciler) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	// Fetch the CassandraDataCenter instance
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := reconciler.client.Get(context.TODO(), request.NamespacedName, cdc); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	requestLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)

	rctx := &reconciliationRequestContext{
		CassandraDataCenterReconciler: *reconciler,
		Request:                       request,
		cdc:                           cdc,
		logger:                        requestLogger,
	}

	rctx.logger.Info("Reconciling CassandraDataCenter.")

	nodesService, err := createOrUpdateNodesService(rctx)
	if err != nil {
		log.Error(err, "Error building nodes service")
		return reconcile.Result{}, err
	}

	seedNodesService, err := createOrUpdateSeedNodesService(rctx)
	if err != nil {
		log.Error(err, "Error building seeds service")
		return reconcile.Result{}, err
	}

	configVolume, err := createOrUpdateOperatorConfigMap(rctx, seedNodesService)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume)
	if err != nil {
		if err == ErrorCDCNotReady {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	rctx.logger.Info("CassandraDataCenter reconciliation complete.")

	return reconcile.Result{}, nil
}

