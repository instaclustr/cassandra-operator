package cassandrabackup

import (
	"context"
	"fmt"
	"github.com/go-logr/logr"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"sigs.k8s.io/controller-runtime/pkg/event"
	"sigs.k8s.io/controller-runtime/pkg/predicate"
	"strconv"
	"strings"
	"sync"
	"time"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandradatacenter"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"
)

var log = logf.Log.WithName("controller_cassandrabackup")

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

	if instance.Status != nil && len(instance.Status) != 0 {
		// when operator is restarted, nothing stops it to react on that CRD and it starts to backup again
		reqLogger.Info("Reconcilliation stopped as backup was already run")
		return reconcile.Result{}, nil
	} else {
		// instance.Status is special because it's a map of host statuses, so if not yet initialised, do it here.
		instance.Status = []*cassandraoperatorv1alpha1.CassandraBackupStatus{}
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

	wg := &sync.WaitGroup{}
	wg.Add(len(sidecarClients))

	podIpHostnameMap := podIpHostnameMap(pods)

	syncedInstance := &syncedInstance{instance: instance, client: r.client}

	for _, sc := range sidecarClients {
		go backup(wg, sc, syncedInstance, podIpHostnameMap[sc.Host], reqLogger)
	}

	wg.Wait()

	return reconcile.Result{}, nil
}

type syncedInstance struct {
	sync.RWMutex
	instance *cassandraoperatorv1alpha1.CassandraBackup
	client   client.Client
}

func backup(
	wg *sync.WaitGroup,
	sidecarClient *sidecar.Client,
	instance *syncedInstance,
	podHostname string,
	logging logr.Logger,
) {

	defer wg.Done()

	backupRequest := &sidecar.BackupRequest{
		StorageLocation:       fmt.Sprintf("%s/%s/%s", instance.instance.Spec.StorageLocation, instance.instance.Spec.CDC, podHostname),
		SnapshotTag:           instance.instance.Spec.SnapshotTag,
		Duration:              instance.instance.Spec.Duration,
		Bandwidth:             instance.instance.Spec.Bandwidth,
		ConcurrentConnections: instance.instance.Spec.ConcurrentConnections,
		Table:                 instance.instance.Spec.Table,
		Keyspaces:             instance.instance.Spec.Keyspaces,
	}

	if operationID, err := sidecarClient.StartOperation(backupRequest); err != nil {
		logging.Error(err, fmt.Sprintf("Error while starting backup operation %v", backupRequest))
	} else {
		for _ = range time.NewTicker(2 * time.Second).C {
			if r, err := sidecarClient.FindBackup(operationID); err != nil {
				logging.Error(err, fmt.Sprintf("Error while finding submitted backup operation %v", operationID))
				break
			} else {
				instance.updateStatus(podHostname, r)

				if r.State == operations.FAILED {
					logging.Info(fmt.Sprintf("Backup operation %v on node %s has failed", operationID, podHostname))
					break
				}

				if r.State == operations.COMPLETED {
					logging.Info(fmt.Sprintf("Backup operation %v on node %s was completed successfully", operationID, podHostname))
					break
				}
			}
		}
	}
}

func podIpHostnameMap(pods []corev1.Pod) map[string]string {

	var podIpHostnameMap = make(map[string]string)

	// make map of pod ips and their hostnames for construction of backup requests
	for _, pod := range pods {
		podIpHostnameMap[pod.Status.PodIP] = pod.Spec.Hostname
	}

	return podIpHostnameMap
}

func (si *syncedInstance) updateStatus(podHostname string, r *sidecar.BackupResponse) {
	defer si.Unlock()
	si.Lock()

	status := &cassandraoperatorv1alpha1.CassandraBackupStatus{Node: podHostname}

	var existingStatus = false

	for _, v := range si.instance.Status {
		if v.Node == podHostname {
			status = v
			existingStatus = true
			break
		}
	}

	status.Progress = fmt.Sprintf("%v%%", strconv.Itoa(int(r.Progress*100)))
	status.State = string(r.State)

	if !existingStatus {
		si.instance.Status = append(si.instance.Status, status)
	}

	si.instance.GlobalProgress = func() string {
		var progresses = 0

		for _, s := range si.instance.Status {
			var i, _ = strconv.Atoi(strings.TrimSuffix(s.Progress, "%"))
			progresses = progresses + i
		}

		return strconv.FormatInt(int64(progresses/len(si.instance.Status)), 10) + "%"
	}()

	si.instance.GlobalStatus = func() string {
		var statuses Statuses = si.instance.Status

		if statuses.contains("FAILED") {
			return "FAILED"
		} else if statuses.contains("PENDING") {
			return "PENDING"
		} else if statuses.contains("RUNNING") {
			return "RUNNING"
		} else if statuses.allMatch("COMPLETED") {
			return "COMPLETED"
		}

		return "UNKNOWN"
	}()

	if err := si.client.Update(context.TODO(), si.instance); err != nil {
		println("error updating CassandraBackup instance")
	}
}

type Statuses []*cassandraoperatorv1alpha1.CassandraBackupStatus

func (statuses Statuses) contains(state string) bool {
	for _, s := range statuses {
		if s.State == state {
			return true
		}
	}
	return false
}

func (statuses Statuses) allMatch(state string) bool {
	for _, s := range statuses {
		if s.State != state {
			return false
		}
	}
	return true
}
