package cassandradatacenter

import (
	"context"
	"errors"
	"strconv"
	"time"

	"github.com/go-logr/logr"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/apps/v1"
	"k8s.io/api/apps/v1beta2"
	corev1 "k8s.io/api/core/v1"
	k8sErrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/resource"
	"k8s.io/apimachinery/pkg/runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"
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
		if k8sErrors.IsNotFound(err) {
			// Request object not found, could have been deleted after reconcile request.
			// Owned objects are automatically garbage collected. For additional cleanup logic use finalizers.
			// Return and don't requeue
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	if defaultOperatorConfig, err := getOperatorDefaultConfig(r.client, request.Namespace, "cassandra-operator-default-config"); err != nil {
		reqLogger.Error(err, "Unable to resolve default operator config from config map 'cassandra-operator-default-config'")
		return reconcile.Result{}, err
	} else if populated, err := populateUnsetFields(instance, defaultOperatorConfig); err != nil {
		reqLogger.Error(err, "Unable to populate unset fields on spec!")
		return reconcile.Result{}, err
	} else if populated == true {
		if err := r.client.Update(context.TODO(), instance); err != nil {
			return reconcile.Result{}, err
		} else {
			return reconcile.Result{}, nil
		}
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

	if rctx.cdc.Spec.PrometheusSupport {
		if err := createOrUpdatePrometheusServiceMonitor(rctx); err != nil {
			return reconcile.Result{}, err
		}
	}

	statefulSet, err := createOrUpdateStatefulSet(rctx, configVolume)
	if err != nil {
		return reconcile.Result{}, err
	}

	_, _, _ = nodesService, seedNodesService, statefulSet

	rctx.logger.Info("CassandraDataCenter reconciliation complete.")

	return reconcile.Result{}, nil
}

func getOperatorDefaultConfig(c client.Client, namespace string, defaultOperatorConfigMapName string) (corev1.ConfigMap, error) {
	defaultConfig := &corev1.ConfigMap{}

	if err := c.Get(context.TODO(), client.ObjectKey{Namespace: namespace, Name: defaultOperatorConfigMapName}, defaultConfig); err == nil {
		return *defaultConfig, nil
	} else if k8sErrors.IsNotFound(err) {
		// if it is not found, just return empty config map
		return *defaultConfig, nil
	} else {
		// otherwise there is something shady going on
		return *defaultConfig, err
	}
}

func populateUnsetFields(instance *cassandraoperatorv1alpha1.CassandraDataCenter, configMap corev1.ConfigMap) (bool, error) {
	populated := false
	if instance.Spec.Nodes == 0 {
		if nodes, ok := configMap.Data["nodes"]; ok {
			if nodes64, err := strconv.ParseInt(nodes, 10, 32); err == nil {
				instance.Spec.Nodes = int32(nodes64)
				populated = true
			} else {
				return false, err
			}
		} else {
			return false, errors.New("'nodes' value is not specified in cassandra-operator-default-config configMap!")
		}
	}

	if len(instance.Spec.CassandraImage) == 0 {
		if cassandraImage, ok := configMap.Data["cassandraImage"]; ok {
			instance.Spec.CassandraImage = cassandraImage
			populated = true
		} else {
			return false, errors.New("'cassandraImage' value is not specified in cassandra-operator-default-config configMap")
		}
	}

	if len(instance.Spec.SidecarImage) == 0 {
		if sidecarImage, ok := configMap.Data["sidecarImage"]; ok {
			instance.Spec.SidecarImage = sidecarImage
			populated = true
		} else {
			return false, errors.New("'sidecarImage' value is not specified in cassandra-operator-default-config configMap")
		}
	}

	if len(instance.Spec.ImagePullPolicy) == 0 {
		instance.Spec.ImagePullPolicy = corev1.PullIfNotPresent
		populated = true
	}

	if instance.Spec.Resources == nil {
		if memory, ok := configMap.Data["memory"]; ok {
			parsedMemory, err := resource.ParseQuantity(memory)

			if err != nil {
				return false, err
			}

			instance.Spec.Resources = &corev1.ResourceRequirements{
				Limits: corev1.ResourceList{
					"memory": parsedMemory,
				},
				Requests: corev1.ResourceList{
					"memory": parsedMemory,
				},
			}
			populated = true
		} else {
			return false, errors.New("'memory' value is not specified in cassandra-operator-default-config configMap")
		}
	}

	if instance.Spec.DummyVolume == nil && instance.Spec.DataVolumeClaimSpec == nil {
		if disk, ok := configMap.Data["disk"]; ok {
			parsedDisk, err := resource.ParseQuantity(disk)

			if err != nil {
				return false, err
			}

			instance.Spec.DummyVolume = &corev1.EmptyDirVolumeSource{
				SizeLimit: &parsedDisk,
			}

			if medium, ok := configMap.Data["diskMedium"]; ok {

				if medium != "" && medium != "Memory" {
					return false, errors.New("'diskMedium' value in cassandra-operator-default-config configMap is not empty string nor 'Memory'")
				}

				instance.Spec.DummyVolume.Medium = corev1.StorageMedium(medium)
			} else {
				instance.Spec.DummyVolume.Medium = ""
			}
		}
	}

	return populated, nil
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
