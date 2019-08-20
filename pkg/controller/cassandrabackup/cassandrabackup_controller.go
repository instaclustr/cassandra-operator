package cassandrabackup

import (
	"context"
	"fmt"
	"strconv"
	"time"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandradatacenter"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	corev1 "k8s.io/api/core/v1"
	v1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/event"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/predicate"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"
)

var log = logf.Log.WithName("controller_cassandrabackup")

/**
* USER ACTION REQUIRED: This is a scaffold file intended for the user to modify with their own Controller
* business logic.  Delete these comments after modifying this file.*
 */

// Add creates a new CassandraBackup Controller and adds it to the Manager. The Manager will set fields on the Controller
// and Start it when the Manager is Started.
func Add(mgr manager.Manager) error {
	return add(mgr, newReconciler(mgr))
}

// newReconciler returns a new reconcile.Reconciler
func newReconciler(mgr manager.Manager) reconcile.Reconciler {
	return &ReconcileCassandraBackup{client: mgr.GetClient(), scheme: mgr.GetScheme()}
}

// add adds a new Controller to mgr with r as the reconcile.Reconciler
func add(mgr manager.Manager, r reconcile.Reconciler) error {
	// Create a new controller
	c, err := controller.New("cassandrabackup-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// TODO: do we want this
	// Filter event types for BackupCRD
	pred := predicate.Funcs{
		// Always handle create events
		CreateFunc: func(e event.CreateEvent) bool {
			return true
		},
		// Always ignore changes. Meaning that the backup CRD is inactionable after creation.
		UpdateFunc: func(e event.UpdateEvent) bool {
			return false
		},
	}

	// Watch for changes to primary resource CassandraBackup
	err = c.Watch(&source.Kind{Type: &cassandraoperatorv1alpha1.CassandraBackup{}}, &handler.EnqueueRequestForObject{}, pred)
	if err != nil {
		return err
	}

	// TODO(user): Modify this to be the types you create that are owned by the primary resource
	// Watch for changes to secondary resource Pods and requeue the owner CassandraBackup
	err = c.Watch(&source.Kind{Type: &corev1.Pod{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &cassandraoperatorv1alpha1.CassandraBackup{},
	})
	if err != nil {
		return err
	}

	return nil
}

// blank assignment to verify that ReconcileCassandraBackup implements reconcile.Reconciler
var _ reconcile.Reconciler = &ReconcileCassandraBackup{}

// ReconcileCassandraBackup reconciles a CassandraBackup object
type ReconcileCassandraBackup struct {
	// This client, initialized using mgr.Client() above, is a split client
	// that reads objects from the cache and writes to the apiserver
	client   client.Client
	scheme   *runtime.Scheme
	recorder record.EventRecorder
}

// Reconcile reads that state of the cluster for a CassandraBackup object and makes changes based on the state read
// and what is in the CassandraBackup.Spec
// TODO(user): Modify this Reconcile function to implement your Controller logic.  This example creates
// a Pod as an example
// Note:
// The Controller will requeue the Request to be processed again if the returned error is non-nil or
// Result.Requeue is true, otherwise upon completion it will remove the work from the queue.
func (r *ReconcileCassandraBackup) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling CassandraBackup")

	// Fetch the CassandraBackup instance
	instance := &cassandraoperatorv1alpha1.CassandraBackup{}
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

	// instance.Status is special because it's a map of host statuses, so if not yet initialised, do it here.
	if instance.Status == nil {
		instance.Status = make(map[string]*cassandraoperatorv1alpha1.CassandraBackupStatus)
	}

	// Get Pod Clients.
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := r.client.Get(context.TODO(), types.NamespacedName{Name: instance.Spec.CDC, Namespace: instance.Namespace}, cdc); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	pods, err := cassandradatacenter.AllPodsInCDC(r.client, cdc)
	if err != nil {
		return reconcile.Result{}, fmt.Errorf("unable to list pods")
	}

	// Use the following for testing:
	sidecarClients := sidecar.SidecarClients(pods, &sidecar.DefaultSidecarClientOptions)

	// Run backups
	for _, sidecarClient := range sidecarClients {

		// TODO: run this on goroutine so that all 3 are handled in parallel
		backupRequest := &sidecar.BackupRequest{
			DestinationUri: instance.Spec.DestinationUri,
			SnapshotName:   instance.Spec.SnapshotName,
			Keyspaces:      instance.Spec.Keyspaces,
		}

		// TODO - maybe log that backup request itself?
		r.recorder.Event(instance, v1.EventTypeNormal, "Received Backup Request", "Starting backup")

		operationID, err := sidecarClient.StartOperation(backupRequest)
		if err != nil {
			reqLogger.Error(err, "Could not submit backup request")
			continue
		} else {
			go func() {
				opState := operations.RUNNING
				// TODO - what if getBackup will return FAILED for ever? This loop would never end ...
				// TODO - maybe extract this to separate method so we can eventualy log error from this loop too?
				for opState != operations.COMPLETED && opState != operations.FAILED {
					backup, err := sidecarClient.FindBackup(operationID)
					if err != nil {
						reqLogger.Error(err, fmt.Sprintf("couldn't find backup operation %v on node %v", operationID, sidecarClient.Host))
						return
					}
					instance.Status[sidecarClient.Host] = &cassandraoperatorv1alpha1.CassandraBackupStatus{
						Progress: fmt.Sprintf("%v%%", strconv.Itoa(int(backup.Progress*100))),
						State:    string(backup.State),
					}
					err = r.client.Update(context.TODO(), instance)
					if err != nil {
						reqLogger.Error(err, "could not update backup crd")
					}
					opState = backup.State
					<-time.After(time.Second)
				}

				if opState == operations.FAILED {
					log.Info(fmt.Sprintf("backup operation %v on node %v has failed", operationID, sidecarClient.Host))
					r.recorder.Event(instance, v1.EventTypeWarning, "Operation Failed", fmt.Sprintf("Backup on node %v has failed", sidecarClient.Host))
				} else if opState == operations.COMPLETED {
					log.Info(fmt.Sprintf("backup operation %v on node %v has finished successfully", operationID, sidecarClient.Host))
					r.recorder.Event(instance, v1.EventTypeNormal, "Operation Finished", fmt.Sprintf("Backup completed on node %v has finished successfully", sidecarClient.Host))
				}

			}()
		}

	}

	// TODO - logging is maybe better?
	reqLogger.Info(fmt.Sprintf("Spec: %v\n", instance.Spec))

	return reconcile.Result{}, nil
}
