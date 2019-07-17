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

	// fine, so we have a mismatch. Will seek to reconcile.
	rackSpec, err := getRackSpec(rctx, request)
	if err != nil {
		return reconcile.Result{}, err
	}

	configVolume, err := createOrUpdateOperatorConfigMap(rctx, seedNodesService, rackSpec)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume, rackSpec)
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	rctx.logger.Info("CassandraDataCenter reconciliation complete.")

	return reconcile.Result{}, nil
}

func getRackSpec(rctx *reconciliationRequestContext, request reconcile.Request) (*Rack, error) {

	// This currently works with the following logic:
	// 1. Build a struct of racks with distribution numbers.
	// 2. Check if all racks (stateful sets) have been created. If not, create a new missing one, and pass it the number
	// of the nodes it's supposed to have.
	// 3. If all racks are present, iterate and check if the expected distribution replicas match the status, if not - reconcile.

	// TODO: For now, call racks "rack#num", where #num starts with 1. Later, figure out the node placement mechanics and
	//  the way to get a consistent ordering for this distribution
	racksDistribution := make(map[string]int32)
	var i int32
	for ; i < rctx.cdc.Spec.Racks; i++ {
		rack := fmt.Sprintf("rack%v", i+1)
		nodes := rctx.cdc.Spec.Nodes / rctx.cdc.Spec.Racks
		if i < (rctx.cdc.Spec.Nodes % rctx.cdc.Spec.Racks) {
			nodes = nodes + 1
		}
		racksDistribution[rack] = nodes
	}

	// Get all stateful sets
	sets, err := getStatefulSets(rctx, request.NamespacedName)
	if err != nil {
		log.Error(err, "Can't find Stateful Sets")
		return nil, err
	}

	// check if all required racks are built. If not, create a missing one.
	rackNum := int32(len(sets.Items))
	if rackNum != rctx.cdc.Spec.Racks {
		rack := fmt.Sprintf("rack%v", rackNum+1)
		return &Rack{Name: rack, Replicas: racksDistribution[rack]}, nil
	}

	// Otherwise, we have all stateful sets running. Let's see which one we should reconcile.
	for _, sts := range sets.Items {
		rack := sts.Labels[rackKey]
		if racksDistribution[rack] != sts.Status.Replicas {
			// reconcile
			return &Rack{Name: rack, Replicas: racksDistribution[rack]}, nil
		}
	}

	return nil, fmt.Errorf("couldn't find a rack to reconcile")

}

func getStatefulSets(rctx *reconciliationRequestContext, namespacedName types.NamespacedName) (*v1.StatefulSetList, error) {
	sts := &v1.StatefulSetList{}
	if err := rctx.client.List(context.TODO(), &client.ListOptions{Namespace: namespacedName.Namespace}, sts); err != nil {
		if errors.IsNotFound(err) {
			return &v1.StatefulSetList{}, nil
		}
		return &v1.StatefulSetList{}, err
	}
	return sts, nil
}

type Rack struct {
	Name     string
	Replicas int32
}
