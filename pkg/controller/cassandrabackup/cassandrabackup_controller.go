package cassandrabackup

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/go-logr/logr"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"sigs.k8s.io/controller-runtime/pkg/event"
	"sigs.k8s.io/controller-runtime/pkg/predicate"

	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandradatacenter"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	corev1 "k8s.io/api/core/v1"
	k8sErrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	logf "sigs.k8s.io/controller-runtime/pkg/log"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
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
	return &ReconcileCassandraBackup{
		client:   mgr.GetClient(),
		scheme:   mgr.GetScheme(),
		recorder: mgr.GetEventRecorderFor("cassandrabackup-controller"),
	}
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

	// Fetch the CassandraBackup backup
	instance := &cassandraoperatorv1alpha1.CassandraBackup{}

	if err := r.client.Get(context.TODO(), request.NamespacedName, instance); err != nil {
		if k8sErrors.IsNotFound(err) {
			// if the resource is not found, that means all of
			// the finalizers have been removed, and the resource has been deleted,
			// so there is nothing left to do.
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	if instance.Status != nil && len(instance.Status) != 0 {
		// when operator is restarted, nothing stops it to react on that CRD and it starts to backup again
		reqLogger.Info(fmt.Sprintf("Reconcilliation of %s stopped as backup was already run", request.NamespacedName))
		return reconcile.Result{}, nil
	} else {
		instance.Status = []*cassandraoperatorv1alpha1.CassandraBackupStatus{}
	}

	if exists, err := backupOfSameSnapshotExists(r.client, instance); err != nil {
		return reconcile.Result{}, err
	} else if exists {
		// we can not backup with same snapshot, cdc and storageLocation
		r.recorder.Event(
			instance,
			corev1.EventTypeWarning,
			"BackupSkipped",
			fmt.Sprintf("cdc %s in cluster %s was not backed up to %s under snapshot %s because such backup already exists",
				instance.Spec.Datacenter, instance.Spec.Cluster, instance.Spec.StorageLocation, instance.Spec.SnapshotTag))
		return reconcile.Result{}, nil
	}

	if instance.JustCreate {
		return reconcile.Result{}, nil
	}

	// fetch secret and make sure it exists

	secret := &corev1.Secret{}
	if err := r.client.Get(context.TODO(), types.NamespacedName{Name: instance.Secret, Namespace: instance.Namespace}, secret); err != nil {
		if k8sErrors.IsNotFound(err) {
			// if the resource is not found, that means all of
			// the finalizers have been removed, and the resource has been deleted,
			// so there is nothing left to do.
			reqLogger.Info(fmt.Sprintf("Secret used for backups %s was not found", instance.Secret))
			return reconcile.Result{}, nil
		}
		// Error reading the object - requeue the request.
		return reconcile.Result{}, err
	}

	// based on storage location, be sure that respective secret entry is there so we error out asap
	if err := validateBackupSecret(secret, instance, reqLogger); err != nil {
		return reconcile.Result{}, err
	}

	// Get Datacenter.
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := r.client.Get(context.TODO(), types.NamespacedName{Name: instance.Spec.CDC, Namespace: instance.Namespace}, cdc); err != nil {
		if k8sErrors.IsNotFound(err) {
			r.recorder.Event(
				instance,
				corev1.EventTypeWarning,
				"FailureEvent",
				fmt.Sprintf("cdc %s of cluster %s to backup not found", instance.Spec.Datacenter, instance.Spec.Cluster))

			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	// Get Pod clients
	pods, err := cassandradatacenter.AllPodsInCDC(r.client, cdc)
	if err != nil {
		return reconcile.Result{}, fmt.Errorf("unable to list pods")
	}

	sidecarClients := sidecar.SidecarClients(pods, &sidecar.DefaultSidecarClientOptions)

	wg := &sync.WaitGroup{}
	wg.Add(len(sidecarClients))

	podIpHostnameMap := podIpHostnameMap(pods)

	syncedInstance := &syncedInstance{backup: instance, client: r.client}

	for _, sc := range sidecarClients {
		go backup(wg, sc, syncedInstance, podIpHostnameMap[sc.Host], reqLogger, r.recorder)
	}

	wg.Wait()

	r.recorder.Event(
		instance,
		corev1.EventTypeNormal,
		"BackupFinished",
		fmt.Sprintf("cdc %s of cluster %s was backed up to %s under snapshot %s", instance.Spec.Datacenter, instance.Spec.Cluster, instance.Spec.StorageLocation, instance.Spec.SnapshotTag))

	return reconcile.Result{}, nil
}

func backupOfSameSnapshotExists(c client.Client, instance *cassandraoperatorv1alpha1.CassandraBackup) (bool, error) {

	backupsList := &cassandraoperatorv1alpha1.CassandraBackupList{}

	if err := c.List(context.TODO(), backupsList); err != nil {
		return false, err
	}

	for _, existingBackup := range backupsList.Items {
		if existingBackup.Status != nil {
			if existingBackup.Spec.SnapshotTag == instance.Spec.SnapshotTag {
				if existingBackup.Spec.StorageLocation == instance.Spec.StorageLocation {
					if existingBackup.Spec.Cluster == instance.Spec.Cluster {
						if existingBackup.Spec.Datacenter == instance.Spec.Datacenter {
							return true, nil
						}
					}
				}
			}
		}
	}

	return false, nil
}

func validateBackupSecret(secret *corev1.Secret, backup *cassandraoperatorv1alpha1.CassandraBackup, logger logr.Logger) error {
	if backup.IsGcpBackup() {
		if len(secret.Data["gcp"]) == 0 {
			return errors.New(fmt.Sprintf("gcp key for secret %s is not set", secret.Name))
		}
	}

	if backup.IsAzureBackup() {
		if len(secret.Data["azurestorageaccount"]) == 0 {
			return errors.New(fmt.Sprintf("azurestorageaccount key for secret %s is not set", secret.Name))
		}

		if len(secret.Data["azurestoragekey"]) == 0 {
			return errors.New(fmt.Sprintf("azurestoragekey key for secret %s is not set", secret.Name))
		}
	}

	if backup.IsS3Backup() {
		// we are just logging here because node can have its credentials injected from AWS itself
		if len(secret.Data["awssecretaccesskey"]) == 0 {
			logger.Info(fmt.Sprintf("awssecretaccesskey key for secret %s is not set, backup "+
				"will failover to authentication mechanims of node itself against AWS.", secret.Name))
		}

		if len(secret.Data["awsaccesskeyid"]) == 0 {
			logger.Info(fmt.Sprintf("awsaccesskeyid key for secret %s is not set, backup "+
				"will failover to authentication mechanims of node itself against AWS.", secret.Name))
		}

		if len(secret.Data["awssecretaccesskey"]) != 0 && len(secret.Data["awsaccesskeyid"]) != 0 {
			if len(secret.Data["awsregion"]) == 0 {
				return errors.New(fmt.Sprintf("there is not awsregion property "+
					"while you have set both awssecretaccesskey and awsaccesskeyid in %s secret for backups", secret.Name))
			}
		}

		if len(secret.Data["awsendpoint"]) != 0 && len(secret.Data["awsregion"]) == 0 {
			return errors.New(fmt.Sprintf("awsendpoint is specified but awsregion is not set in %s secret for backups", secret.Name))
		}
	}

	return nil
}

type syncedInstance struct {
	sync.RWMutex
	backup *cassandraoperatorv1alpha1.CassandraBackup
	client client.Client
}

func backup(
	wg *sync.WaitGroup,
	sidecarClient *sidecar.Client,
	instance *syncedInstance,
	podHostname string,
	logging logr.Logger,
	recorder record.EventRecorder) {

	defer wg.Done()

	backupRequest := &sidecar.BackupRequest{
		StorageLocation:       fmt.Sprintf("%s/%s/%s/%s", instance.backup.Spec.StorageLocation, instance.backup.Spec.Cluster, instance.backup.Spec.Datacenter, podHostname),
		SnapshotTag:           instance.backup.Spec.SnapshotTag,
		Duration:              instance.backup.Spec.Duration,
		Bandwidth:             instance.backup.Spec.Bandwidth,
		ConcurrentConnections: instance.backup.Spec.ConcurrentConnections,
		Table:                 instance.backup.Spec.Table,
		Keyspaces:             instance.backup.Spec.Keyspaces,
		Secret:                instance.backup.Secret,
		KubernetesNamespace:   instance.backup.Namespace,
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

					recorder.Event(instance.backup,
						corev1.EventTypeWarning,
						"FailureEvent",
						fmt.Sprintf("Backup operation %v on node %s has failed", operationID, podHostname))

					break
				}

				if r.State == operations.COMPLETED {

					recorder.Event(instance.backup,
						corev1.EventTypeNormal,
						"SuccessEvent",
						fmt.Sprintf("Backup operation %v on node %s was completed.", operationID, podHostname))

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
	si.Lock()
	defer si.Unlock()

	status := &cassandraoperatorv1alpha1.CassandraBackupStatus{Node: podHostname}

	var existingStatus = false

	for _, v := range si.backup.Status {
		if v.Node == podHostname {
			status = v
			existingStatus = true
			break
		}
	}

	status.Progress = fmt.Sprintf("%v%%", strconv.Itoa(int(r.Progress*100)))
	status.State = r.State

	if !existingStatus {
		si.backup.Status = append(si.backup.Status, status)
	}

	si.backup.GlobalProgress = func() string {
		var progresses = 0

		for _, s := range si.backup.Status {
			var i, _ = strconv.Atoi(strings.TrimSuffix(s.Progress, "%"))
			progresses = progresses + i
		}

		return strconv.FormatInt(int64(progresses/len(si.backup.Status)), 10) + "%"
	}()

	si.backup.GlobalStatus = func() operations.OperationState {
		var statuses Statuses = si.backup.Status

		if statuses.contains(operations.FAILED) {
			return operations.FAILED
		} else if statuses.contains(operations.PENDING) {
			return operations.PENDING
		} else if statuses.contains(operations.RUNNING) {
			return operations.RUNNING
		} else if statuses.allMatch(operations.COMPLETED) {
			return operations.COMPLETED
		}

		return operations.UNKNOWN
	}()

	if err := si.client.Update(context.TODO(), si.backup); err != nil {
		println("error updating CassandraBackup backup")
	}
}

type Statuses []*cassandraoperatorv1alpha1.CassandraBackupStatus

func (statuses Statuses) contains(state operations.OperationState) bool {
	for _, s := range statuses {
		if s.State == state {
			return true
		}
	}
	return false
}

func (statuses Statuses) allMatch(state operations.OperationState) bool {
	for _, s := range statuses {
		if s.State != state {
			return false
		}
	}
	return true
}
