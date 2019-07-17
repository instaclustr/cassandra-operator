package cassandrabackup

import (
	"context"
	"fmt"
	"github.com/google/uuid"
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"github.com/instaclustr/cassandra-operator/pkg/common/operations"
	"github.com/instaclustr/cassandra-operator/pkg/controller/cassandradatacenter"
	"github.com/instaclustr/cassandra-operator/pkg/sidecar"
	v1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	"strconv"
	"time"
)

const (
	controllerName = "CassandraBackupController"
)

//"github.com/instaclustr/cassandra-operator/pkg/sidecar"

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

	// Get request params
	b := &cassandraoperatorv1alpha1.CassandraBackup{}
	if err := r.client.Get(context.TODO(), request.NamespacedName, b); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	// b.Status is special because it's a map of host statuses, so if not yet initialised, do it here.
	if b.Status == nil {
		b.Status = make(map[string]*cassandraoperatorv1alpha1.CassandraBackupStatus)
	}

	// Get Pod Clients.
	cdc := &cassandraoperatorv1alpha1.CassandraDataCenter{}
	if err := r.client.Get(context.TODO(), types.NamespacedName{Name: b.Spec.CDC, Namespace: b.Namespace}, cdc); err != nil {
		if errors.IsNotFound(err) {
			return reconcile.Result{}, nil
		}
		return reconcile.Result{}, err
	}

	pods, err := cassandradatacenter.ExistingPods(r.client, cdc)
	if err != nil {
		return reconcile.Result{}, fmt.Errorf("unable to list pods")
	}

	// Use the following for testing:
	sidecarClients := sidecar.SidecarClients(pods, &sidecar.DefaultSidecarClientOptions)

	// Run backups
	for _, sidecarClient := range sidecarClients {

		// TODO: run this on goroutine so that all 3 are handled in parallel
		backupRequest := &sidecar.BackupRequest{
			DestinationUri: b.Spec.DestinationUri,
			SnapshotName:   b.Spec.SnapshotName,
			Keyspaces:      b.Spec.Keyspaces,
		}

		// TODO - maybe log that backup request itself?
		r.recorder.Event(b, v1.EventTypeNormal, "Received Backup Request", "Starting backup")

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
					backup, err := getBackup(sidecarClient, operationID)
					if err != nil {
						reqLogger.Error(err, fmt.Sprintf("couldn't find backup operation %v on node %v", operationID, sidecarClient.Host));
						return
					}
					b.Status[sidecarClient.Host] = &cassandraoperatorv1alpha1.CassandraBackupStatus{
						Progress: fmt.Sprintf("%v%%", strconv.Itoa(int(backup.Progress*100))),
						State:    string(backup.State),
					}
					err = r.client.Update(context.TODO(), b)
					if err != nil {
						reqLogger.Error(err, "could not update backup crd")
					}
					opState = backup.State
					<-time.After(time.Second)
				}

				if opState == operations.FAILED {
					log.Info(fmt.Sprintf("backup operation %v on node %v has failed", operationID, sidecarClient.Host))
					r.recorder.Event(b, v1.EventTypeWarning, "Operation Failed", fmt.Sprintf("Backup on node %v has failed", sidecarClient.Host))
				} else if opState == operations.COMPLETED {
					log.Info(fmt.Sprintf("backup operation %v on node %v has finished successfully", operationID, sidecarClient.Host))
					r.recorder.Event(b, v1.EventTypeNormal, "Operation Finished", fmt.Sprintf("Backup completed on node %v has finished successfully", sidecarClient.Host))
				}

			}()
		}

	}

	// TODO - logging is maybe better?
	reqLogger.Info(fmt.Sprintf("Spec: %v\n", b.Spec))

	return reconcile.Result{}, nil
}

// TODO - maybe move this to operations.go?
func getBackup(client *sidecar.Client, id uuid.UUID) (backup *sidecar.BackupResponse, err error) {

	if op, err := client.GetOperation(id); err != nil {
		return nil, err
	} else if b, err := sidecar.ParseOperation(*op, sidecar.GetType("backup")); err != nil {
		return nil, err
	} else if backup, ok := b.(*sidecar.BackupResponse); !ok {
		return nil, fmt.Errorf("can't parse operation to backup")
	} else {
		return backup, nil
	}
}
