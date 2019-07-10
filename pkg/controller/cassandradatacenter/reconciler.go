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


	// TODO: Multi-racks support
	// This is just some thoughts on the subject, which only supports starting/growing the cluster atm.
	// Things to consider:
	// 1. How to confine StatusSet to a specific AZ? Ideas: pods should select nodes according to labels or node isolation,
	// read up https://kubernetes.io/docs/concepts/configuration/assign-pod-node/. Then StatusSet will automatically pick
	// them up with the labels.
	// ...TBD

	// Parse the object and check if it makes any sense wrt to racks
	if ok, err := checkRacks(cdc.Spec); !ok {
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

	// check if we need to do anything at all, that is the number of nodes matches running ones
	allPods, err := AllPodsInCDC(rctx.client, cdc)
	if int32(len(allPods)) == cdc.Spec.Nodes {
		log.Info("All pods are running, nothing to reconcile")
		return reconcile.Result{}, nil
	} else if err != nil {
		log.Error(err, "can't find all pods, skipping reconcile cycle")
		return reconcile.Result{}, err
	}

	// fine, so we don't have enough nodes running yet. Pick the rack with smallest number of nodes
	// and update it
	rack, err := getRack(rctx, request)
	if err != nil {
		return reconcile.Result{}, err
	}

	configVolume, err := createOrUpdateOperatorConfigMap(rctx, seedNodesService, rack)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume, rack)
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

	// check if all required racks are built. if not, build a missing one.
	rackNum := len(sets.Items)
	if rackNum != len(rctx.cdc.Spec.Racks) {
		return fmt.Sprintf("rack%v", rackNum+1), nil
	}

	// Otherwise, we have all stateful sets running. Let's see which one we should reconcile.
	var rack string
	for _, sts := range sets.Items {
		// current rack
		rack = sts.Labels[rackKey]
		// # of requested pods in a rack
		requestedPods := rctx.cdc.Spec.Racks[rack]
		// # of currently running pods in this set
		pods, err := AllPodsInRack(rctx.client, rctx.cdc.Namespace, sts.Labels)
		if err != nil {
			// TODO: log error and continue? let's break for now
			return "", fmt.Errorf("can't figure out proper rack to reconcile, err: %v", err)
		}
		if int(requestedPods) != len(pods) {
			// Need to reconcile this one for sure
			return rack, nil
		}
	}

	if len(rack) > 0 {
		return rack, nil
	}

	return "", fmt.Errorf("can't figure out proper rack to reconcile")
}

func getSets(rctx *reconciliationRequestContext, namespacedName types.NamespacedName) (*v1.StatefulSetList, error) {
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

	// Check that racks are provided. If not, use 1 rack.
	if spec.Racks == nil || len(spec.Racks) == 0 {
		spec.Racks = make(map[string]int32)
		spec.Racks["rack1"] = spec.Nodes
		return true, nil
	}

	// Check that the number of nodes requested matches racks distribution
	var rackNodes int32
	for _, nodes := range spec.Racks {
		rackNodes = rackNodes + nodes
	}

	// If rack nodes != spec nodes, error out?
	if rackNodes != spec.Nodes {
		return false, fmt.Errorf("total nodes %d is larger than nodes in racks %d", spec.Nodes, rackNodes)
	}

	// Else, return ok
	return true, nil

}
