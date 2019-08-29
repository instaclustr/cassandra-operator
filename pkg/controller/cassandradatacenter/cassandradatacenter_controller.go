package cassandradatacenter

import (
	"context"
	"github.com/go-logr/logr"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/apps/v1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"
	"time"
)

var log = logf.Log.WithName("controller_cassandradatacenter")

// Add creates a new CassandraDataCenter Controller and adds it to the Manager. The Manager will set fields on the Controller
// and Start it when the Manager is Started.
func Add(mgr manager.Manager) error {
	return add(mgr, newReconciler(mgr))
}

// newReconciler returns a new reconcile.Reconciler
func newReconciler(mgr manager.Manager) reconcile.Reconciler {
	return &ReconcileCassandraDataCenter{client: mgr.GetClient(), scheme: mgr.GetScheme()}
}

// add adds a new Controller to mgr with r as the reconcile.Reconciler
func add(mgr manager.Manager, r reconcile.Reconciler) error {
	// Create a new controller
	c, err := controller.New("cassandradatacenter-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// Watch for changes to primary resource CassandraDataCenter
	err = c.Watch(&source.Kind{Type: &cassandraoperatorv1alpha1.CassandraDataCenter{}}, &handler.EnqueueRequestForObject{})
	if err != nil {
		return err
	}

	// Watch for changes to secondary resource Pods and requeue the owner CassandraDataCenter
	for _, t := range []runtime.Object{&corev1.Service{}, &v1beta2.StatefulSet{}, &corev1.ConfigMap{}} {
		requestForOwnerHandler := &handler.EnqueueRequestForOwner{
			IsController: true,
			OwnerType:    &cassandraoperatorv1alpha1.CassandraDataCenter{},
		}

		if err = c.Watch(&source.Kind{Type: t}, requestForOwnerHandler); err != nil {
			return err
		}
	}

	return nil
}

// blank assignment to verify that ReconcileCassandraDataCenter implements reconcile.Reconciler
var _ reconcile.Reconciler = &ReconcileCassandraDataCenter{}

// ReconcileCassandraDataCenter reconciles a CassandraDataCenter object
type ReconcileCassandraDataCenter struct {
	// This client, initialized using mgr.Client() above, is a split client
	// that reads objects from the cache and writes to the apiserver
	client client.Client
	scheme *runtime.Scheme
}

type reconciliationRequestContext struct {
	ReconcileCassandraDataCenter
	logger         logr.Logger
	cdc            *cassandraoperatorv1alpha1.CassandraDataCenter
	sets           []v1.StatefulSet
	operation      scalingOperation
	allPods        []corev1.Pod
	sidecarClients map[*corev1.Pod]*sidecar.Client
}

type scalingOperation string

var (
	scalingUp   = scalingOperation("ScaleUp")
	scalingDown = scalingOperation("ScaleDown")
	noScale     = scalingOperation("")
)

// Reconcile reads that state of the cluster for a CassandraDataCenter object and makes changes based on the state read
// and what is in the CassandraDataCenter.Spec
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (r *ReconcileCassandraDataCenter) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling CassandraDataCenter")

	// Fetch the CassandraDataCenter instance
	instance := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	err := r.client.Get(context.TODO(), request.NamespacedName, instance)
	if err != nil {
		if errors.IsNotFound(err) {
			// Request object not found, could have been deleted after reconcile request.
			// Owned objects are automatically garbage collected. For additional cleanup logic use finalizers.
			// Return and don't requeue
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	// Build new reconcile context
	rctx, err := newReconciliationContext(r, reqLogger, instance)
	if err != nil {
		return reconcile.Result{}, err
	}

	// check cluster health
	if clusterReady, err := checkClusterHealth(rctx); !clusterReady {
		if err == ErrorClusterNotReady {
			// If something is not ready, wait a minute and retry
			return reconcile.Result{RequeueAfter: time.Minute}, nil
		} else {
			return reconcile.Result{}, err
		}
	}

	nodesService, err := createOrUpdateNodesService(rctx)
	if err != nil {
		return reconcile.Result{}, err
	}

	seedNodesService, err := createOrUpdateSeedNodesService(rctx)
	if err != nil {
		return reconcile.Result{}, err
	}

	configVolume, err := createOrUpdateOperatorConfigMap(rctx, seedNodesService)
	if err != nil {
		return reconcile.Result{}, err
	}

	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume)
	if err != nil {
		return reconcile.Result{}, err
	}

	// TODO:
	_, _, _ = nodesService, seedNodesService, statefulSet

	rctx.logger.Info("CassandraDataCenter reconciliation complete.")

	return reconcile.Result{}, nil
}

func newReconciliationContext(r *ReconcileCassandraDataCenter, reqLogger logr.Logger, instance *cassandraoperatorv1alpha1.CassandraDataCenter) (*reconciliationRequestContext, error) {

	// Figure out the scaling operation. If no change needed, then it's noop
	allPods, err := AllPodsInCDC(r.client, instance)
	if err != nil {
		return nil, err
	}

	var op scalingOperation
	if instance.Spec.Nodes > int32(len(allPods)) {
		op = scalingUp
	} else if instance.Spec.Nodes < int32(len(allPods)) {
		op = scalingDown
	}

	// build out the context
	rctx := &reconciliationRequestContext{
		ReconcileCassandraDataCenter: *r,
		cdc:                          instance,
		operation:                    op,
		allPods:                      allPods,
		sidecarClients:               sidecar.SidecarClients(allPods, &sidecar.DefaultSidecarClientOptions),
		logger:                       reqLogger,
	}

	// update the stateful sets
	rctx.sets, err = getStatefulSets(rctx)
	if err != nil {
		return nil, err
	}

	return rctx, nil
}
