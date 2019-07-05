package cassandradatacenter

import (
	"context"
	"fmt"
	"github.com/go-logr/logr"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	v1 "k8s.io/api/apps/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
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
		log.Error(err, "Error finding the CDC object in k8")
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	requestLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)

	//Parse the object and check if it makes any sense wrt to racks
	if ok, err := checkRacks(cdc.Spec); !ok {
	    // log error and exit
		return reconcile.Result{}, err
	}

	rctx := &reconciliationRequestContext{
		CassandraDataCenterReconciler: *reconciler,
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

	// figure out the number of stateful sets we're expected to have (1 per rack).
	// check the number of sets, if doesn't match -> create a new one for missing rack.
	// if match, select the set with the lowest number of nodes, -> that's our rack.

	// check if we need to do anything at all
	allPods, err := AllPodsInCDC(rctx.client, cdc)
	if int32(len(allPods)) == cdc.Spec.Nodes {
		log.Info("All pods are created")
		return reconcile.Result{}, nil
	} else if err != nil {
		log.Error(err, "can't find all pods")
		return reconcile.Result{}, err
	}

	// fine, so we don't have enough nodes running yet. Pick the rack with smallest number of nodes
	// and update it
	rack, err := getRack(rctx, request);
	if err != nil {
		return reconcile.Result{}, err
	}

	configVolume, err := createOrUpdateOperatorConfigMap(rctx, seedNodesService, rack)
	if err != nil {
		return reconcile.Result{}, err
	}

	// # of pods per stateful set is replicas/racks
	numPods := int32(cdc.Spec.Nodes/cdc.Spec.Racks)
	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume, rack, numPods)
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	rctx.logger.Info("CassandraDataCenter reconciliation complete.")

	return reconcile.Result{}, nil
}

func getRack(rctx *reconciliationRequestContext, request reconcile.Request) (string, error) {

	// Get all stateful sets
	sets, err := getSets(rctx, request.NamespacedName)
	if err != nil {
		log.Error(err, "Can't find Stateful Sets")
		return "", err
	}

	// check if all racks are built. if not, build a missing one.
	rackNum := int32(len(sets.Items))
	if rackNum != rctx.cdc.Spec.Racks {
		return fmt.Sprintf("rack%v", rackNum+1), nil
	}

	// Otherwise, we have all stateful sets running. Let's see which one we should add to.
	// number of pods is initialised to the total nodes.
	var rack string
	minNodes := rctx.cdc.Spec.Nodes
	for _, sts := range sets.Items {
		pods, err := AllPodsInRack(rctx.client, rctx.cdc.Namespace, sts.Labels)
		if err != nil {
			// TODO: log error and continue? let's break for now
			return "", fmt.Errorf("can't figure out proper rack, err: %v", err)
		}
		if minNodes > int32(len(pods)) {
			rack = sts.Labels[rackKey]
		}
	}

	if len(rack) > 0 {
		return rack, nil
	}

	return "", fmt.Errorf("can't figure out proper rack")
}

func getSets(rctx *reconciliationRequestContext, namespacedName types.NamespacedName ) (*v1.StatefulSetList, error) {
	sts := &v1.StatefulSetList{}
	if err := rctx.client.List(context.TODO(), &client.ListOptions{Namespace: namespacedName.Namespace}, sts); err != nil {
		if errors.IsNotFound(err) {
			return &v1.StatefulSetList{}, nil
		}
		return &v1.StatefulSetList{}, err
	}
	return sts, nil
}

func checkRacks(spec cassandraoperatorv1alpha1.CassandraDataCenterSpec) (bool, error) {
	// if nodes mod racks != 0, false, otherwise true
	if spec.Nodes % spec.Racks != 0 {
		return false, fmt.Errorf("the number of racks should be able to evenly distribute all the nodes")
	}

	return true, nil


}
